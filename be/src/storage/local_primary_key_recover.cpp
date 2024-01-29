// Copyright 2021-present StarRocks, Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "storage/local_primary_key_recover.h"

#include "storage/chunk_helper.h"
#include "storage/tablet_meta_manager.h"
#include "storage/update_manager.h"

namespace starrocks {

Status LocalPrimaryKeyRecover::pre_cleanup() {
    // remove pk index
    auto index_entry = _update_mgr->index_cache().get(_tablet->tablet_id());
    if (index_entry != nullptr) {
        _update_mgr->index_cache().remove(index_entry);
    }
    // delete persistent index meta
    if (_tablet->get_enable_persistent_index()) {
        RETURN_IF_ERROR(TabletMetaManager::clear_persistent_index(_tablet->data_dir(), &_wb, _tablet->tablet_id()));
    }
    // Notice. We don't remove delvec here, otherwise replace delvec by latest apply version later
    return Status::OK();
}

starrocks::Schema LocalPrimaryKeyRecover::generate_pkey_schema() {
    const starrocks::TabletSchema& tablet_schema = _tablet->tablet_schema();
    vector<ColumnId> pk_columns(tablet_schema.num_key_columns());
    for (auto i = 0; i < tablet_schema.num_key_columns(); i++) {
        pk_columns[i] = (ColumnId)i;
    }
    return ChunkHelper::convert_schema(tablet_schema, pk_columns);
}

Status LocalPrimaryKeyRecover::sort_rowsets(std::vector<RowsetSharedPtr>* rowsets) {
    std::sort(rowsets->begin(), rowsets->end(), [](const RowsetSharedPtr& a, const RowsetSharedPtr& b) {
        const RowsetMetaSharedPtr& rowset_meta_a = a->rowset_meta();
        const RowsetMetaSharedPtr& rowset_meta_b = b->rowset_meta();
        // if rowset was generated by compaction, use max compact input rowset id as its compare id
        uint32_t rowset_a_comp_id = rowset_meta_a->has_max_compact_input_rowset_id()
                                            ? rowset_meta_a->max_compact_input_rowset_id()
                                            : rowset_meta_a->get_rowset_seg_id();
        uint32_t rowset_b_comp_id = rowset_meta_b->has_max_compact_input_rowset_id()
                                            ? rowset_meta_b->max_compact_input_rowset_id()
                                            : rowset_meta_b->get_rowset_seg_id();
        return rowset_a_comp_id < rowset_b_comp_id;
    });
    return Status::OK();
}

Status LocalPrimaryKeyRecover::rowset_iterator(
        const starrocks::Schema& pkey_schema, OlapReaderStatistics& stats,
        const std::function<Status(const std::vector<ChunkIteratorPtr>&,
                                   const std::vector<std::unique_ptr<RandomAccessFile>>&, const std::vector<uint32_t>&,
                                   uint32_t)>& handler) {
    std::vector<RowsetSharedPtr> rowsets;
    std::vector<uint32_t> rowset_ids;
    int64_t latest_applied_major_version;
    RETURN_IF_ERROR(_tablet->updates()->get_latest_applied_version(&_latest_applied_version));
    RETURN_IF_ERROR(
            _tablet->updates()->get_apply_version_and_rowsets(&latest_applied_major_version, &rowsets, &rowset_ids));
    DCHECK(latest_applied_major_version == _latest_applied_version.major());
    // Sort the rowsets in order of primary key occurrence,
    // so we can generate correct delvecs
    RETURN_IF_ERROR(sort_rowsets(&rowsets));
    for (auto& rowset : rowsets) {
        // NOT acquire rowset reference because tbalet already in error state, rowset reclaim should stop
        // NOT apply delvec when create segment iterator
        // 1. get iterator for each segment
        auto res = rowset->get_segment_iterators2(pkey_schema, nullptr, latest_applied_major_version, &stats);
        if (!res.ok()) {
            return res.status();
        }
        auto& itrs = res.value();
        // 2. get delete read files
        CHECK(itrs.size() == rowset->num_segments()) << "itrs.size != num_segments";
        std::vector<std::unique_ptr<RandomAccessFile>> del_rfs;
        ASSIGN_OR_RETURN(auto fs, FileSystem::CreateSharedFromString(rowset->rowset_path()));
        std::vector<uint32_t> delidxs;
        for (int idx = 0; idx < rowset->num_delete_files(); idx++) {
            auto path = Rowset::segment_del_file_path(rowset->rowset_path(), rowset->rowset_id(), idx);
            ASSIGN_OR_RETURN(auto read_file, fs->new_random_access_file(path));
            del_rfs.push_back(std::move(read_file));
            delidxs.push_back(rowset->rowset_meta()->get_meta_pb().delfile_idxes(idx));
        }
        RETURN_IF_ERROR(handler(itrs, del_rfs, delidxs, rowset->rowset_meta()->get_rowset_seg_id()));
    }
    return Status::OK();
}

// generate delvec and save
Status LocalPrimaryKeyRecover::finalize_delvec(const PrimaryIndex::DeletesMap& new_deletes) {
    size_t ndelvec = new_deletes.size();
    vector<std::pair<uint32_t, DelVectorPtr>> new_del_vecs(ndelvec);
    size_t idx = 0;
    // generate delvec
    for (auto& new_delete : new_deletes) {
        uint32_t rssid = new_delete.first;
        // it's newly added rowset's segment, do not have latest delvec yet
        new_del_vecs[idx].first = rssid;
        new_del_vecs[idx].second = std::make_shared<DelVector>();
        auto& del_ids = new_delete.second;
        new_del_vecs[idx].second->init(_latest_applied_version.major(), del_ids.data(), del_ids.size());
        idx++;
        LOG(INFO) << "LocalPrimaryKeyRecover finalize delvec, rssid: " << rssid << " del cnt: " << del_ids.size();
    }
    // put delvec into WriteBatch
    RETURN_IF_ERROR(TabletMetaManager::put_del_vectors(_tablet->data_dir(), &_wb, _tablet->tablet_id(),
                                                       _latest_applied_version, new_del_vecs));
    // sync to RocksDB
    RETURN_IF_ERROR(_tablet->data_dir()->get_meta()->write_batch(&_wb));
    // put delvec in cache
    TabletSegmentId tsid;
    tsid.tablet_id = _tablet->tablet_id();
    for (auto& delvec_pair : new_del_vecs) {
        tsid.segment_id = delvec_pair.first;
        // need to clear delvec first, so we can set new delvec successfully
        _update_mgr->clear_cached_del_vec({tsid});
        RETURN_IF_ERROR(_update_mgr->set_cached_del_vec(tsid, delvec_pair.second));
    }
    return Status::OK();
}

int64_t LocalPrimaryKeyRecover::tablet_id() {
    return _tablet->tablet_id();
}

} // namespace starrocks