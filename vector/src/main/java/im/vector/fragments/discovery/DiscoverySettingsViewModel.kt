/*
 * Copyright 2019 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package im.vector.fragments.discovery

import android.text.TextUtils
import androidx.core.net.toUri
import com.airbnb.mvrx.*
import im.vector.Matrix
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.core.callback.ApiCallback
import org.matrix.androidsdk.core.model.MatrixError
import org.matrix.androidsdk.features.identityserver.IdentityServerManager
import org.matrix.androidsdk.rest.model.pid.ThirdPartyIdentifier
import org.matrix.androidsdk.rest.model.pid.ThreePid


data class PidInfo(
        val value: String,
        val isShared: Async<SharedState>,
        val _3pid: ThreePid? = null
) {
    enum class SharedState {
        SHARED,
        NOT_SHARED,
        NOT_VERIFIED
    }
}

data class PidState(val value: String, val isShared: Async<Boolean>)

data class DiscoverySettingsState(
        val modalLoadingState: Async<Boolean> = Success(false),
        val identityServer: Async<String?> = Success(null),
        val emailList: Async<List<PidInfo>> = Success(emptyList()),
        val phoneNumbersList: Async<List<PidInfo>> = Success(emptyList())
) : MvRxState

class DiscoverySettingsViewModel(initialState: DiscoverySettingsState, private val mxSession: MXSession?) : BaseMvRxViewModel<DiscoverySettingsState>(initialState, false) {

    fun changeIdentityServer(server: String?) {
        setState {
            copy(modalLoadingState = Loading())
        }

        mxSession!!.identityServerManager.setIdentityServerUrl(server, object : ApiCallback<Void?> {
            override fun onSuccess(info: Void?) {
                setState {
                    copy(
                            modalLoadingState = Success(false),
                            identityServer = Success(server)
                    )
                }
                refreshModel()
            }

            override fun onUnexpectedError(e: Exception?) {
                setState {
                    copy(
                            modalLoadingState = Success(false)
//                            identityServer = Success(server)
                    )
                }
            }

            override fun onNetworkError(e: Exception?) {
                setState {
                    copy(
                            modalLoadingState = Success(false)
//                            identityServer = Success(server)
                    )
                }
            }

            override fun onMatrixError(e: MatrixError?) {
                setState {
                    copy(
                            modalLoadingState = Success(false)
//                            identityServer = Success(server)
                    )
                }
            }

        })
    }

    fun shareEmail(email: String) = withState { state ->
        if (state.identityServer.invoke() == null) return@withState
        val currentMails = state.emailList.invoke() ?: return@withState
        setState {
            copy(emailList = Success(
                    currentMails.map {
                        if (it.value == email) {
                            it.copy(isShared = Loading())
                        } else {
                            it
                        }
                    })
            )
        }

        val pid = ThirdPartyIdentifier().apply {
            medium = ThreePid.MEDIUM_EMAIL
            address = email
        }
        mxSession?.myUser?.delete3Pid(pid, object : ApiCallback<Void?> {
            override fun onSuccess(info: Void?) {
                request3pidToken(email)
            }

            override fun onUnexpectedError(e: java.lang.Exception) {
                handleDeleteError(e)
            }

            override fun onNetworkError(e: java.lang.Exception) {
                handleDeleteError(e)
            }

            override fun onMatrixError(e: MatrixError) {
                handleDeleteError(Exception(e.message))
            }


            private fun handleDeleteError(e: java.lang.Exception) {
                setState {
                    val currentMails = emailList.invoke() ?: emptyList()
                    copy(emailList = Success(
                            currentMails.map {
                                if (it.value == email) {
                                    it.copy(
                                            isShared = Fail(e))
                                } else {
                                    it
                                }
                            }
                    ))
                }
            }

        })
    }

    private fun request3pidToken(email: String) {
        val threePid = ThreePid(email, ThreePid.MEDIUM_EMAIL)
        mxSession?.myUser?.requestEmailValidationToken(
                mxSession.identityServerManager.getIdentityServerUrl()?.toUri(),
                threePid,
                object : ApiCallback<Void?> {
                    override fun onSuccess(info: Void?) {
                        setState {
                            val currentMails = emailList.invoke() ?: emptyList()
                            copy(emailList = Success(
                                    currentMails.map {
                                        if (it.value == email) {
                                            it.copy(
                                                    _3pid = threePid,
                                                    isShared = Success(PidInfo.SharedState.NOT_VERIFIED)
                                            )
                                        } else {
                                            it
                                        }
                                    }
                            ))
                        }
                    }

                    override fun onUnexpectedError(e: java.lang.Exception) {
                        reportError(e)
                    }

                    override fun onNetworkError(e: java.lang.Exception) {
                        reportError(e)
                    }

                    private fun reportError(e: java.lang.Exception) {
                        setState {
                            val currentMails = emailList.invoke() ?: emptyList()
                            copy(emailList = Success(
                                    currentMails.map {
                                        if (it.value == email) {
                                            it.copy(isShared = Fail(e))
                                        } else {
                                            it
                                        }
                                    }
                            ))
                        }
                    }


                    override fun onMatrixError(e: MatrixError) {
                        reportError(java.lang.Exception(e.message))
                        if (TextUtils.equals(MatrixError.THREEPID_IN_USE, e.errcode)) {
                            // This may just mean they are dealing with an old homeserver,
                            // versus the 3PID already being bound on this homeserver by another user.
                            // -> Delete 3pid then retry?
                        }
                    }

                })
    }

    fun revokeEmail(email: String) = withState { state ->
        //Fake call
        val currentMails = state.emailList.invoke() ?: return@withState
        val updated = currentMails.map {
            if (it.value == email) {
                it.copy(isShared = Loading())
            } else {
                it
            }
        }

    }

    fun revokePN(pn: String) = withState { state ->
        //Fake call
        val currentPN = state.phoneNumbersList.invoke() ?: return@withState
        val updated = currentPN.map {
            if (it.value == pn) {
                it.copy(isShared = Loading())
            } else {
                it
            }
        }
        setState {
            copy(phoneNumbersList = Success(updated))
        }
        GlobalScope.launch {
            kotlinx.coroutines.delay(1000)
            setState {
                val currentPN = phoneNumbersList.invoke() ?: emptyList()
                copy(phoneNumbersList = Success(
                        currentPN.map {
                            if (it.value == pn) {
                                it.copy(isShared = Success(PidInfo.SharedState.NOT_SHARED))
                            } else {
                                it
                            }
                        }
                ))
            }
        }
    }

    fun sharePN(pn: String) = withState { state ->
        //Fake call
        val currentPN = state.phoneNumbersList.invoke() ?: return@withState
        val updated = currentPN.map {
            if (it.value == pn) {
                it.copy(isShared = Loading())
            } else {
                it
            }
        }
        setState {
            copy(phoneNumbersList = Success(updated))
        }
        GlobalScope.launch {
            kotlinx.coroutines.delay(1000)
            setState {
                val currentPN = phoneNumbersList.invoke() ?: emptyList()
                copy(phoneNumbersList = Success(
                        currentPN.map {
                            if (it.value == pn) {
                                it.copy(isShared = Success(PidInfo.SharedState.SHARED))
                            } else {
                                it
                            }
                        }
                ))
            }
        }
    }

    fun startListenToIdentityManager() {
        setState {
            copy(identityServer = Success(mxSession?.identityServerManager?.getIdentityServerUrl()))
        }
        mxSession?.identityServerManager?.addListener(identityServerManagerListener)
    }

    fun stopListenToIdentityManager() {
        mxSession?.identityServerManager?.addListener(identityServerManagerListener)
    }

    private val identityServerManagerListener = object : IdentityServerManager.IdentityServerManagerListener {
        override fun onIdentityServerChange() = withState { state ->
            val identityServerUrl = mxSession?.identityServerManager?.getIdentityServerUrl()
            val currentIS = state.identityServer.invoke()
            setState {
                copy(identityServer = Success(identityServerUrl))
            }
            if (currentIS != identityServerUrl) refreshModel()
        }
    }

    fun refreshModel() = withState { state ->

        if (state.identityServer.invoke().isNullOrBlank()) return@withState

        setState {
            copy(emailList = Loading())
        }

        mxSession?.myUser?.refreshThirdPartyIdentifiers(object : ApiCallback<Void> {
            override fun onUnexpectedError(e: java.lang.Exception) {
                setState {
                    copy(emailList = Fail(e))
                }
            }

            override fun onNetworkError(e: java.lang.Exception) {
                setState {
                    copy(emailList = Fail(e))
                }
            }

            override fun onMatrixError(e: MatrixError) {
                setState {
                    copy(emailList = Fail(Throwable(e.message)))
                }
            }

            override fun onSuccess(info: Void?) {
                val linkedMailsInfo = mxSession.myUser.getlinkedEmails()
                setState {
                    copy(
                            emailList = Success(
                                    linkedMailsInfo?.map { PidInfo(it.address, Loading()) }
                                            ?: emptyList()
                            )
                    )
                }

                val knownEmailList = linkedMailsInfo.map { it.address }
                mxSession.identityServerManager?.lookup3Pids(knownEmailList, linkedMailsInfo.map { it.medium },
                        object : ApiCallback<List<String>> {
                            override fun onSuccess(info: List<String>?) {
                                setState {
                                    copy(
                                            emailList = Success(linkedMailsInfo.map {
                                                val hasMatrxId = info?.get(knownEmailList.indexOf(it.address))?.isBlank()?.not()
                                                        ?: false
                                                PidInfo(
                                                        value = it.address,
                                                        isShared = Success(PidInfo.SharedState.SHARED.takeIf { hasMatrxId }
                                                                ?: PidInfo.SharedState.NOT_SHARED)
                                                )
                                            })
                                    )
                                }
                            }


                            override fun onUnexpectedError(e: java.lang.Exception?) {
                                //What to do?
                            }

                            override fun onNetworkError(e: java.lang.Exception?) {
                                //What to do?
                            }

                            override fun onMatrixError(e: MatrixError?) {
                                //What to do?
                            }

                        })

                val linkedPNInfo = mxSession.myUser.getlinkedPhoneNumbers()
                setState {
                    copy(
                            phoneNumbersList = Success(
                                    linkedPNInfo?.map { PidInfo(it.address, Loading()) }
                                            ?: emptyList()
                            )
                    )
                }

                val knownPns = linkedPNInfo.map { it.address }
                mxSession.identityServerManager?.lookup3Pids(knownPns, linkedPNInfo.map { it.medium },
                        object : ApiCallback<List<String>> {
                            override fun onSuccess(info: List<String>?) {
                                setState {
                                    copy(
                                            phoneNumbersList = Success(linkedPNInfo.map {
                                                val hasMatrixId = (info?.get(knownPns.indexOf(it.address))?.isBlank()?.not()
                                                        ?: false)
                                                PidInfo(
                                                        value = it.address,
                                                        isShared = Success(PidInfo.SharedState.SHARED.takeIf { hasMatrixId }
                                                                ?: PidInfo.SharedState.NOT_SHARED)
                                                )
                                            })
                                    )
                                }
                            }


                            override fun onUnexpectedError(e: java.lang.Exception?) {
                                //What to do?
                            }

                            override fun onNetworkError(e: java.lang.Exception?) {
                                //What to do?
                            }

                            override fun onMatrixError(e: MatrixError?) {
                                //What to do?
                            }

                        })
            }

        })
    }

    fun add3pid(email: String, bind: Boolean) {
        setState {
            val currentMails = emailList.invoke() ?: emptyList()
            copy(emailList = Success(
                    currentMails.map {
                        if (it.value == email) {
                            it.copy(isShared = Loading())
                        } else {
                            it
                        }
                    }
            ))
        }
        withState { state ->
            val _3pid = state.emailList.invoke()?.find { it.value == email }?._3pid
            mxSession?.myUser?.add3Pid(
                    mxSession.identityServerManager.getIdentityServerUrl()?.toUri(),
                    _3pid,
                    bind,
                    object : ApiCallback<Void?> {
                        override fun onSuccess(info: Void?) {
                            setState {
                                val currentMails = emailList.invoke() ?: emptyList()
                                copy(emailList = Success(
                                        currentMails.map {
                                            if (it.value == email) {
                                                it.copy(isShared = Success(
                                                        if (bind) PidInfo.SharedState.SHARED else PidInfo.SharedState.NOT_SHARED
                                                ))
                                            } else {
                                                it
                                            }
                                        }
                                ))
                            }
                        }

                        override fun onUnexpectedError(e: java.lang.Exception) {
                            reportError(e)
                        }

                        override fun onNetworkError(e: java.lang.Exception) {
                            reportError(e)
                        }

                        private fun reportError(e: java.lang.Exception) {
                            setState {
                                val currentMails = emailList.invoke() ?: emptyList()
                                copy(emailList = Success(
                                        currentMails.map {
                                            if (it.value == email) {
                                                it.copy(
                                                        isShared = Success(PidInfo.SharedState.NOT_VERIFIED)
                                                )
                                            } else {
                                                it
                                            }
                                        }
                                ))
                            }
                        }


                        override fun onMatrixError(e: MatrixError) {
                            reportError(java.lang.Exception(e.message))
                        }

                    })
        }
    }

    companion object : MvRxViewModelFactory<DiscoverySettingsViewModel, DiscoverySettingsState> {

        override fun create(viewModelContext: ViewModelContext, state: DiscoverySettingsState): DiscoverySettingsViewModel? {
            val matrixId = viewModelContext.args<String>()
            val mxSession = Matrix.getInstance(viewModelContext.activity).getSession(matrixId)
            val viewModel = DiscoverySettingsViewModel(state, mxSession)

            mxSession?.identityServerManager?.getIdentityServerUrl().let {
                viewModel.setState {
                    copy(identityServer = Success(it))
                }
            }

            viewModel.refreshModel()

            return viewModel
        }

        override fun initialState(viewModelContext: ViewModelContext): DiscoverySettingsState? {

            return DiscoverySettingsState(
                    identityServer = Success(null),
                    emailList = Success(emptyList()),
                    phoneNumbersList = Success(emptyList())
            )
        }
    }

}