package com.martinvidal.tv.data.net

import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Contrato REST del portal (`api/portalCore/...`), reconstruido del APK.
 *
 * Todas las rutas se invocan por POST con cuerpo
 * `{"commonParams":{...},"specificParams":{...}}` y devuelven un
 * [PortalEnvelope]. Las rutas de pagos, cupones y canjes se han excluido por
 * alcance.
 */
interface PortalApi {

    @POST("api/portalCore/addSubscribe")
    suspend fun epAddSubscribe(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/bindPhone")
    suspend fun epBindPhone(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/blSearchByContent")
    suspend fun epBlSearchByContent(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/blSearchByName")
    suspend fun epBlSearchByName(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/changeBindPhone")
    suspend fun epChangeBindPhone(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/checkForceBind")
    suspend fun epCheckForceBind(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/checkVerifiCode")
    suspend fun epCheckVerifiCode(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/config/get")
    suspend fun epConfigGet(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/delFavorite")
    suspend fun epDelFavorite(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/delSubscribe")
    suspend fun epDelSubscribe(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/device")
    suspend fun epDevice(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/device/updateOrInsert")
    suspend fun epDeviceUpdateOrInsert(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/emailResetPassNotice")
    suspend fun epEmailResetPassNotice(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/epg/v2/getAllMatch")
    suspend fun epEpgV2GetAllMatch(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/epg/v2/getLineUps")
    suspend fun epEpgV2GetLineUps(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/epg/v2/getMatchDetail")
    suspend fun epEpgV2GetMatchDetail(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/epg/v2/getScoreboard")
    suspend fun epEpgV2GetScoreboard(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/epg/v2/getShelveMatch")
    suspend fun epEpgV2GetShelveMatch(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/epg/v2/getTeamEvent")
    suspend fun epEpgV2GetTeamEvent(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/epg/v3/getFootballMatch")
    suspend fun epEpgV3GetFootballMatch(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/epg/v4/getNearestMatch")
    suspend fun epEpgV4GetNearestMatch(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/epg/v5/getNearestMatch")
    suspend fun epEpgV5GetNearestMatch(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/feedback/getCustomerService")
    suspend fun epFeedbackGetCustomerService(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/feedback/userFeedBack")
    suspend fun epFeedbackUserFeedBack(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/getAllbackProgram")
    suspend fun epGetAllbackProgram(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/getAreaCode")
    suspend fun epGetAreaCode(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/getBaseTime")
    suspend fun epGetBaseTime(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/getEmailSuffix")
    suspend fun epGetEmailSuffix(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/getFavorite")
    suspend fun epGetFavorite(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/getHome")
    suspend fun epGetHome(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/getItemByPerson")
    suspend fun epGetItemByPerson(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/getNextColumns")
    suspend fun epGetNextColumns(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/getPerson")
    suspend fun epGetPerson(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/getPropertiesInfo")
    suspend fun epGetPropertiesInfo(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/getRecommendColumnContents")
    suspend fun epGetRecommendColumnContents(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/getSubscribe")
    suspend fun epGetSubscribe(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/getVerifiCode")
    suspend fun epGetVerifiCode(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/liveSearchByName")
    suspend fun epLiveSearchByName(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/pwdCheck")
    suspend fun epPwdCheck(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/qr/allowLogin")
    suspend fun epQrAllowLogin(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/qr/token")
    suspend fun epQrToken(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/register")
    suspend fun epRegister(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/searchResourceOrPerson")
    suspend fun epSearchResourceOrPerson(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/setPassword")
    suspend fun epSetPassword(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/terminalAuth")
    suspend fun epTerminalAuth(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/unBindEmail")
    suspend fun epUnBindEmail(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/updateBindEmailOrPwd")
    suspend fun epUpdateBindEmailOrPwd(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/v10/startPlayVOD")
    suspend fun epV10StartPlayVOD(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/v14/getSlbInfo")
    suspend fun epV14GetSlbInfo(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/v15/getSlbInfo")
    suspend fun epV15GetSlbInfo(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/v2/addFavorite")
    suspend fun epV2AddFavorite(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/v2/bindEmail")
    suspend fun epV2BindEmail(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/v2/changeBindEmail")
    suspend fun epV2ChangeBindEmail(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/v2/delFavorite")
    suspend fun epV2DelFavorite(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/v2/delSubscribe")
    suspend fun epV2DelSubscribe(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/v2/sendEmailVerifyCode")
    suspend fun epV2SendEmailVerifyCode(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/v2/unBind")
    suspend fun epV2UnBind(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/v2/updateRestrictedStatus")
    suspend fun epV2UpdateRestrictedStatus(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/v2/validateVerifyCode")
    suspend fun epV2ValidateVerifyCode(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/v3/filterByContent")
    suspend fun epV3FilterByContent(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/v3/filterGenre")
    suspend fun epV3FilterGenre(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/v3/getColumnContents")
    suspend fun epV3GetColumnContents(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/v3/getColumnRecommendResource")
    suspend fun epV3GetColumnRecommendResource(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/v3/getProgram")
    suspend fun epV3GetProgram(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/v3/getRecommends")
    suspend fun epV3GetRecommends(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/v3/getShelveData")
    suspend fun epV3GetShelveData(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/v3/searchByContent")
    suspend fun epV3SearchByContent(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/v3/searchByName")
    suspend fun epV3SearchByName(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/v3/snToken")
    suspend fun epV3SnToken(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/v4/getItemData")
    suspend fun epV4GetItemData(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/v4/resetPwd")
    suspend fun epV4ResetPwd(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/v4/startPlayLive")
    suspend fun epV4StartPlayLive(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/v5/heartbeat")
    suspend fun epV5Heartbeat(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/v5/loginOut")
    suspend fun epV5LoginOut(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/v5/qr/getResult")
    suspend fun epV5QrGetResult(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/v5/startPlayLive")
    suspend fun epV5StartPlayLive(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/v6/getLiveData")
    suspend fun epV6GetLiveData(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/v6/startPlayBTV")
    suspend fun epV6StartPlayBTV(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/v7/getLiveData")
    suspend fun epV7GetLiveData(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/v8/active")
    suspend fun epV8Active(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/v8/login")
    suspend fun epV8Login(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope
    @POST("api/portalCore/v9/getAuthInfo")
    suspend fun epV9GetAuthInfo(@Body body: Map<String, @JvmSuppressWildcards Any?> = emptyMap()): PortalEnvelope

}
