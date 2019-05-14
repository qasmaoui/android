/**
 * ownCloud Android client application
 *
 * @author David González Verdugo
 * Copyright (C) 2019 ownCloud GmbH.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.owncloud.android.shares.repository

import androidx.lifecycle.LiveData
import com.owncloud.android.AppExecutors
import com.owncloud.android.NetworkBoundResource
import com.owncloud.android.lib.resources.shares.ShareParserResult
import com.owncloud.android.lib.resources.shares.ShareType
import com.owncloud.android.shares.datasource.LocalSharesDataSource
import com.owncloud.android.shares.datasource.RemoteSharesDataSource
import com.owncloud.android.shares.db.OCShare
import com.owncloud.android.vo.Resource

class OCShareRepository(
    private val appExecutors: AppExecutors,
    private val localSharesDataSource: LocalSharesDataSource,
    private val remoteSharesDataSource: RemoteSharesDataSource
) : ShareRepository {

    companion object Factory {
        fun create(
            appExecutors: AppExecutors = AppExecutors(),
            localSharesDataSource: LocalSharesDataSource,
            remoteSharesDataSource: RemoteSharesDataSource
        ): OCShareRepository = OCShareRepository(
            appExecutors,
            localSharesDataSource,
            remoteSharesDataSource
        )
    }

    override fun loadSharesForFile(
        filePath: String,
        accountName: String,
        shareTypes: List<ShareType>,
        reshares: Boolean,
        subfiles: Boolean
    ): LiveData<Resource<List<OCShare>>> {
        return object : NetworkBoundResource<List<OCShare>, ShareParserResult>(appExecutors) {
            override fun saveCallResult(item: ShareParserResult) {
                val sharesForFileFromServer = item.shares.map { remoteShare ->
                    OCShare.fromRemoteShare(remoteShare).also { it.accountOwner = accountName }
                }

                if (sharesForFileFromServer.isEmpty()) {
                    localSharesDataSource.deleteSharesForFile(filePath, accountName)
                }

                localSharesDataSource.replaceSharesForFile(sharesForFileFromServer)
            }

            override fun shouldFetch(data: List<OCShare>?) = true

            override fun loadFromDb(): LiveData<List<OCShare>> =
                localSharesDataSource.getSharesForFileAsLiveData(filePath, accountName, shareTypes)

            override fun createCall() = remoteSharesDataSource.getSharesForFile(filePath, reshares, subfiles)

        }.asLiveData()
    }

    override fun insertPublicShareForFile(
        filePath: String,
        accountName: String,
        permissions: Int,
        name: String,
        password: String,
        expirationTimeInMillis: Long,
        publicUpload: Boolean
    ): LiveData<Resource<List<OCShare>>> {
        return object : NetworkBoundResource<List<OCShare>, ShareParserResult>(appExecutors) {
            override fun saveCallResult(item: ShareParserResult) {
                val newShareForFileFromServer = item.shares.map { remoteShare ->
                    OCShare.fromRemoteShare(remoteShare).also { it.accountOwner = accountName }
                }

                localSharesDataSource.insert(newShareForFileFromServer)
            }

            override fun shouldFetch(data: List<OCShare>?) = true

            override fun loadFromDb(): LiveData<List<OCShare>> {
                return localSharesDataSource.getSharesForFileAsLiveData(
                    filePath, accountName, listOf(ShareType.PUBLIC_LINK)
                )
            }

            override fun createCall() = remoteSharesDataSource.insertShareForFile(
                filePath,
                ShareType.PUBLIC_LINK,
                "",
                permissions,
                name,
                password,
                expirationTimeInMillis,
                publicUpload
            )
        }.asLiveData()
    }

    override fun updatePublicShareForFile(
        filePath: String,
        accountName: String,
        remoteId: Long,
        name: String,
        password: String?,
        expirationDateInMillis: Long,
        permissions: Int,
        publicUpload: Boolean
    ): LiveData<Resource<List<OCShare>>> {
        return object : NetworkBoundResource<List<OCShare>, ShareParserResult>(appExecutors) {
            override fun saveCallResult(item: ShareParserResult) {
                val updatedShareForFileFromServer = item.shares.map { remoteShare ->
                    OCShare.fromRemoteShare(remoteShare).also { it.accountOwner = accountName }
                }

                localSharesDataSource.update(updatedShareForFileFromServer.first())
            }

            override fun shouldFetch(data: List<OCShare>?): Boolean {
                return true
            }

            override fun loadFromDb(): LiveData<List<OCShare>> {
                return localSharesDataSource.getSharesForFileAsLiveData(
                    filePath, accountName, listOf(ShareType.PUBLIC_LINK)
                )
            }

            override fun createCall() = remoteSharesDataSource.updateShareForFile(
                remoteId,
                name,
                password,
                expirationDateInMillis,
                permissions,
                publicUpload
            )
        }.asLiveData()
    }

    override fun deletePublicShare(
        filePath: String,
        accountName: String,
        remoteId: Long
    ): LiveData<Resource<List<OCShare>>> {
        return object : NetworkBoundResource<List<OCShare>, ShareParserResult>(appExecutors) {
            override fun saveCallResult(item: ShareParserResult) {
                localSharesDataSource.deleteShare(remoteId)
            }

            override fun shouldFetch(data: List<OCShare>?): Boolean {
                return true
            }

            override fun loadFromDb(): LiveData<List<OCShare>> {
                return localSharesDataSource.getSharesForFileAsLiveData(
                    filePath, accountName, listOf(ShareType.PUBLIC_LINK)
                )
            }

            override fun createCall() = remoteSharesDataSource.deleteShare(remoteId)
        }.asLiveData()
    }
}
