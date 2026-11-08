package com.novatrade.demo.trading

import com.novatrade.demo.model.Account
import com.novatrade.demo.model.AccountType
import com.novatrade.demo.model.CloseReason
import com.novatrade.demo.model.ClosedTrade
import com.novatrade.demo.model.OrderType
import com.novatrade.demo.model.PendingOrder
import com.novatrade.demo.model.Position
import com.novatrade.demo.model.Side
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** All of the user's (virtual) accounts and which one is selected. */
data class AccountBook(val accounts: List<Account>, val activeNumber: Long) {
    val active: Account get() = accounts.first { it.number == activeNumber }
}

/** Persists the account book as a small JSON file in app storage. */
class AccountStore(private val file: File) {

    fun load(): AccountBook? = runCatching {
        if (!file.exists()) return null
        val o = JSONObject(file.readText())
        if (o.has("accounts")) {
            AccountBook(o.getJSONArray("accounts").map { it.toAccount() }, o.getLong("active"))
        } else {
            // Single-account file from the first version of the app.
            val acc = o.toAccount()
            AccountBook(listOf(acc), acc.number)
        }
    }.getOrNull()

    fun save(book: AccountBook) {
        val o = JSONObject()
            .put("active", book.activeNumber)
            .put("accounts", JSONArray(book.accounts.map { it.toJson() }))
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(o.toString())
        tmp.renameTo(file)
    }

    private fun Account.toJson() = JSONObject()
        .put("number", number)
        .put("type", type.name)
        .put("kind", kind)
        .put("nickname", nickname)
        .put("platform", platform)
        .put("balance", balance)
        .put("leverage", leverage)
        .put("nextId", nextId)
        .put("totalDeposits", totalDeposits)
        .put("positions", JSONArray(positions.map { it.toJson() }))
        .put("pending", JSONArray(pending.map { it.toJson() }))
        .put("history", JSONArray(history.map { it.toJson() }))

    private fun JSONObject.toAccount() = Account(
        number = getLong("number"),
        type = AccountType.valueOf(optString("type", AccountType.DEMO.name)),
        kind = optString("kind", "Standard"),
        nickname = optString("nickname", ""),
        platform = optString("platform", com.novatrade.demo.Brand.name),
        balance = getDouble("balance"),
        leverage = getInt("leverage"),
        positions = getJSONArray("positions").map { it.toPosition() },
        pending = optJSONArray("pending")?.map { it.toPending() }.orEmpty(),
        history = getJSONArray("history").map { it.toClosedTrade() },
        nextId = getLong("nextId"),
        totalDeposits = getDouble("totalDeposits"),
    )

    private fun Position.toJson() = JSONObject()
        .put("id", id).put("symbol", symbol).put("side", side.name).put("lots", lots)
        .put("openPrice", openPrice).put("openTime", openTime).put("margin", margin)
        .put("stopLoss", stopLoss ?: JSONObject.NULL).put("takeProfit", takeProfit ?: JSONObject.NULL)

    private fun JSONObject.toPosition() = Position(
        id = getLong("id"), symbol = getString("symbol"), side = Side.valueOf(getString("side")),
        lots = getDouble("lots"), openPrice = getDouble("openPrice"), openTime = getLong("openTime"),
        margin = getDouble("margin"), stopLoss = optDoubleOrNull("stopLoss"), takeProfit = optDoubleOrNull("takeProfit"),
    )

    private fun PendingOrder.toJson() = JSONObject()
        .put("id", id).put("symbol", symbol).put("side", side.name).put("type", type.name).put("lots", lots)
        .put("price", price).put("createdTime", createdTime)
        .put("stopLoss", stopLoss ?: JSONObject.NULL).put("takeProfit", takeProfit ?: JSONObject.NULL)

    private fun JSONObject.toPending() = PendingOrder(
        id = getLong("id"), symbol = getString("symbol"), side = Side.valueOf(getString("side")),
        type = OrderType.valueOf(getString("type")), lots = getDouble("lots"), price = getDouble("price"),
        createdTime = getLong("createdTime"), stopLoss = optDoubleOrNull("stopLoss"), takeProfit = optDoubleOrNull("takeProfit"),
    )

    private fun ClosedTrade.toJson() = JSONObject()
        .put("id", id).put("symbol", symbol).put("side", side.name).put("lots", lots)
        .put("openPrice", openPrice).put("closePrice", closePrice).put("openTime", openTime)
        .put("closeTime", closeTime).put("profit", profit).put("reason", reason.name)
        .put("stopLoss", stopLoss).put("takeProfit", takeProfit)

    private fun JSONObject.toClosedTrade() = ClosedTrade(
        id = getLong("id"), symbol = getString("symbol"), side = Side.valueOf(getString("side")),
        lots = getDouble("lots"), openPrice = getDouble("openPrice"), closePrice = getDouble("closePrice"),
        openTime = getLong("openTime"), closeTime = getLong("closeTime"), profit = getDouble("profit"),
        reason = CloseReason.valueOf(getString("reason")),
        stopLoss = optDoubleOrNull("stopLoss"), takeProfit = optDoubleOrNull("takeProfit"),
    )

    private fun JSONObject.optDoubleOrNull(key: String): Double? = if (isNull(key)) null else getDouble(key)

    private fun <T> JSONArray.map(transform: (JSONObject) -> T): List<T> =
        List(length()) { transform(getJSONObject(it)) }
}
