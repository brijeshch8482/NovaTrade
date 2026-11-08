package com.novatrade.demo.model

/** Every tradable instrument, grouped like the Trade screen's tabs. */
object Instruments {
    /** Most traded first; also the default "popularity" order. */
    private val POPULAR = listOf(
        "XAUUSD", "BTCUSD", "EURUSD", "USTEC", "GBPUSD", "USDJPY", "ETHUSD", "US30", "USOIL", "US500",
        "XAGUSD", "AAPL", "NVDA", "TSLA", "GBPJPY", "AUDUSD", "USDCAD", "SOLUSD", "XRPUSD", "UKOIL",
        "EURJPY", "USDCHF", "NZDUSD", "DE30", "BNBUSD", "DOGEUSD",
    )

    private fun pop(symbol: String) = POPULAR.indexOf(symbol).let { if (it < 0) 999 else it }

    private val CCY_NAMES = mapOf(
        "EUR" to "Euro", "USD" to "US Dollar", "GBP" to "Great Britain Pound", "JPY" to "Japanese Yen",
        "CHF" to "Swiss Franc", "AUD" to "Australian Dollar", "CAD" to "Canadian Dollar", "NZD" to "New Zealand Dollar",
        "HKD" to "Hong Kong Dollar", "SGD" to "Singapore Dollar", "TRY" to "Turkish Lira", "ZAR" to "South African Rand",
        "MXN" to "Mexican Peso", "NOK" to "Norwegian Krone", "SEK" to "Swedish Krona", "PLN" to "Polish Zloty",
        "THB" to "Thai Baht", "INR" to "Indian Rupee",
    )

    private fun fx(
        base: String, quote: String, category: Category, price: Double, vol: Double, spread: Double,
        lev: Int = 2000, digits: Int = if (quote == "JPY") 3 else 5,
    ): Instrument {
        val ticker = if (base == "USD") "$quote=X" else "$base$quote=X"
        val symbol = base + quote
        return Instrument(
            symbol = symbol, display = "$base/$quote", name = "${CCY_NAMES[base]} vs ${CCY_NAMES[quote]}",
            category = category, digits = digits, contractSize = 100_000.0, spreadFraction = spread,
            source = PriceSource.Yahoo(ticker), fallbackPrice = price, tickVolatility = vol, maxLeverage = lev,
            quoteCcy = quote, icons = listOf(base, quote), hours = Hours.FX, popularity = pop(symbol),
        )
    }

    private fun crypto(
        coin: String, name: String, price: Double, digits: Int, contract: Double, lev: Int = 200,
        spread: Double = 0.0006, symbol: String = coin + "USD",
    ) = Instrument(
        symbol = symbol, display = coin, name = "$name vs US Dollar", category = Category.CRYPTO, digits = digits,
        contractSize = contract, spreadFraction = spread, source = PriceSource.Binance(coin + "USDT"),
        fallbackPrice = price, tickVolatility = 0.00015, maxLeverage = lev, icons = listOf(coin),
        // Spot best bid/ask can sit still for seconds; light noise keeps the quote ticking like a broker feed.
        hours = Hours.ALWAYS, popularity = pop(symbol), liveJitter = 0.00002,
    )

    private fun yahoo(
        symbol: String, display: String, name: String, category: Category, ticker: String, price: Double,
        digits: Int, contract: Double, lev: Int, spread: Double, vol: Double, icons: List<String>,
        quoteCcy: String = "USD", hours: Hours = Hours.FX,
    ) = Instrument(
        symbol = symbol, display = display, name = name, category = category, digits = digits, contractSize = contract,
        spreadFraction = spread, source = PriceSource.Yahoo(ticker), fallbackPrice = price, tickVolatility = vol,
        maxLeverage = lev, quoteCcy = quoteCcy, icons = icons, hours = hours, popularity = pop(symbol),
    )

    private fun stock(ticker: String, name: String, price: Double) = yahoo(
        ticker, ticker, name, Category.STOCKS, ticker, price, digits = 2, contract = 1.0, lev = 20,
        spread = 0.0005, vol = 0.00008, icons = listOf("@$ticker"), hours = Hours.US_STOCKS,
    )

    private fun index(symbol: String, name: String, ticker: String, price: Double, country: String, ccy: String) = yahoo(
        symbol, symbol, name, Category.INDICES, ticker, price, digits = 2, contract = 1.0, lev = 400,
        spread = 0.0002, vol = 0.00004, icons = listOf("#$country"), quoteCcy = ccy,
    )

    val all: List<Instrument> = listOf(
        // Majors
        fx("EUR", "USD", Category.MAJORS, 1.1143, 0.000012, 0.00009),
        fx("GBP", "USD", Category.MAJORS, 1.3356, 0.000014, 0.0001),
        fx("USD", "JPY", Category.MAJORS, 158.425, 0.000015, 0.0001),
        fx("USD", "CHF", Category.MAJORS, 0.82, 0.000013, 0.00013),
        fx("AUD", "USD", Category.MAJORS, 0.6868, 0.000016, 0.00012),
        fx("USD", "CAD", Category.MAJORS, 1.4128, 0.000011, 0.00012),
        fx("NZD", "USD", Category.MAJORS, 0.5548, 0.000016, 0.00015),

        // Metals: gold follows live PAXG on Binance and trades around the clock.
        Instrument(
            symbol = "XAUUSD", display = "XAU/USD", name = "Gold vs US Dollar", category = Category.METALS, digits = 2,
            contractSize = 100.0, spreadFraction = 0.00006, source = PriceSource.Binance("PAXGUSDT"), fallbackPrice = 4065.0,
            tickVolatility = 0.00004, maxLeverage = 2000, icons = listOf("XAU", "USD"), hours = Hours.ALWAYS,
            popularity = pop("XAUUSD"), liveJitter = 0.00005,
        ),
        yahoo("XAGUSD", "XAG/USD", "Silver vs US Dollar", Category.METALS, "SI=F", 51.942, 3, 5000.0, 1000, 0.0006, 0.00006, listOf("XAG", "USD")),
        yahoo("XPTUSD", "XPT/USD", "Platinum vs US Dollar", Category.METALS, "PL=F", 1536.9, 2, 100.0, 200, 0.001, 0.00008, listOf("XPT", "USD")),
        yahoo("XPDUSD", "XPD/USD", "Palladium vs US Dollar", Category.METALS, "PA=F", 1017.1, 2, 100.0, 200, 0.0012, 0.0001, listOf("XPD", "USD")),
        yahoo("XCUUSD", "XCU/USD", "Copper vs US Dollar", Category.METALS, "HG=F", 6.238, 4, 10_000.0, 200, 0.001, 0.00006, listOf("XCU", "USD")),

        // Crypto (live from Binance)
        crypto("BTC", "Bitcoin", 75963.57, 2, 1.0, lev = 400, spread = 0.00012),
        crypto("ETH", "Ethereum", 2246.21, 2, 1.0, lev = 400, spread = 0.00015),
        crypto("BNB", "BNB", 694.19, 2, 1.0),
        crypto("SOL", "Solana", 89.97, 3, 1.0, spread = 0.0003),
        crypto("XRP", "Ripple", 1.1711, 5, 1.0, spread = 0.0004),
        crypto("ADA", "Cardano", 0.183, 5, 1000.0),
        crypto("DOGE", "Dogecoin", 0.07196, 6, 10_000.0),
        crypto("TRX", "TRON", 0.3197, 5, 1000.0),
        crypto("TON", "Toncoin", 1.6, 4, 100.0),
        crypto("AVAX", "Avalanche", 8.279, 3, 100.0),
        crypto("DOT", "Polkadot", 0.904, 4, 1000.0),
        crypto("LINK", "Chainlink", 10.818, 3, 100.0),
        crypto("LTC", "Litecoin", 52.19, 2, 10.0),
        crypto("BCH", "Bitcoin Cash", 256.2, 2, 1.0),
        crypto("XLM", "Stellar", 0.1527, 5, 1000.0),
        crypto("UNI", "Uniswap", 7.023, 3, 100.0),
        crypto("ATOM", "Cosmos", 1.384, 4, 100.0),
        crypto("ETC", "Ethereum Classic", 6.94, 3, 100.0),
        crypto("NEAR", "NEAR Protocol", 3.696, 4, 100.0),
        crypto("APT", "Aptos", 0.6014, 4, 1000.0),
        crypto("ARB", "Arbitrum", 0.166, 5, 1000.0),
        crypto("OP", "Optimism", 0.1031, 5, 1000.0),
        crypto("SUI", "Sui", 0.8622, 4, 1000.0),
        crypto("FIL", "Filecoin", 0.8031, 4, 1000.0),
        crypto("ICP", "Internet Computer", 2.418, 3, 100.0),
        crypto("HBAR", "Hedera", 0.06778, 5, 10_000.0),
        crypto("AAVE", "Aave", 131.41, 2, 10.0),
        crypto("INJ", "Injective", 8.189, 3, 100.0),
        crypto("SHIB", "Shiba Inu", 0.00000449, 9, 10_000_000.0),
        crypto("PEPE", "Pepe", 0.00000493, 9, 10_000_000.0),
        Instrument(
            symbol = "ETHBTC", display = "ETH/BTC", name = "Ethereum vs Bitcoin", category = Category.CRYPTO, digits = 6,
            contractSize = 1.0, spreadFraction = 0.0004, source = PriceSource.Binance("ETHBTC"), fallbackPrice = 0.02957,
            tickVolatility = 0.00015, maxLeverage = 200, quoteCcy = "BTC", icons = listOf("ETH", "BTC"), hours = Hours.ALWAYS, liveJitter = 0.00002,
        ),

        // Indices (futures-based CFDs, profit in the index's currency)
        index("US30", "US Wall Street 30 Index", "YM=F", 51385.0, "US", "USD"),
        index("US500", "US SPX 500 Index", "ES=F", 7402.0, "US", "USD"),
        index("USTEC", "US Tech 100 Index", "NQ=F", 27085.25, "US", "USD"),
        index("DE30", "Germany 30 Index", "^GDAXI", 25820.36, "DE", "EUR"),
        index("UK100", "UK 100 Index", "^FTSE", 10673.94, "GB", "GBP"),
        index("STOXX50", "EU Stocks 50 Index", "^STOXX50E", 6170.49, "EU", "EUR"),
        index("FR40", "France 40 Index", "^FCHI", 8139.0, "FR", "EUR"),
        index("JP225", "Japan 225 Index", "^N225", 57434.77, "JP", "JPY"),
        index("HK50", "Hong Kong 50 Index", "^HSI", 24481.86, "HK", "HKD"),
        index("AUS200", "Australia 200 Index", "^AXJO", 9020.8, "AU", "AUD"),

        // Stocks (US hours)
        stock("AAPL", "Apple Inc.", 342.37),
        stock("MSFT", "Microsoft Corporation", 530.61),
        stock("NVDA", "NVIDIA Corporation", 186.64),
        stock("TSLA", "Tesla, Inc.", 358.22),
        stock("AMZN", "Amazon.com, Inc.", 259.3),
        stock("GOOGL", "Alphabet Inc.", 284.78),
        stock("META", "Meta Platforms, Inc.", 610.15),
        stock("NFLX", "Netflix, Inc.", 63.41),
        stock("AMD", "Advanced Micro Devices, Inc.", 449.04),
        stock("INTC", "Intel Corporation", 56.28),
        stock("JPM", "JPMorgan Chase & Co.", 334.49),
        stock("BA", "The Boeing Company", 173.4),
        stock("DIS", "The Walt Disney Company", 116.75),
        stock("KO", "The Coca-Cola Company", 95.67),
        stock("PG", "Procter & Gamble Co.", 165.4),

        // Energies
        yahoo("USOIL", "USOIL", "Crude Oil", Category.ENERGIES, "CL=F", 90.83, 3, 1000.0, 200, 0.0005, 0.00008, listOf("OIL")),
        yahoo("UKOIL", "UKOIL", "Crude Oil Brent", Category.ENERGIES, "BZ=F", 98.32, 3, 1000.0, 200, 0.0005, 0.00008, listOf("OIL")),
        yahoo("XNGUSD", "XNG/USD", "Natural Gas vs US Dollar", Category.ENERGIES, "NG=F", 2.998, 4, 10_000.0, 100, 0.001, 0.0001, listOf("GAS")),

        // Exotic
        fx("USD", "HKD", Category.EXOTIC, 7.8396, 0.000003, 0.0001, lev = 200),
        fx("USD", "SGD", Category.EXOTIC, 1.2825, 0.000012, 0.0003, lev = 200),
        fx("USD", "TRY", Category.EXOTIC, 50.1343, 0.00002, 0.001, lev = 50),
        fx("USD", "ZAR", Category.EXOTIC, 16.517, 0.00003, 0.0008, lev = 200),
        fx("USD", "MXN", Category.EXOTIC, 18.5975, 0.00003, 0.0008, lev = 200),
        fx("USD", "NOK", Category.EXOTIC, 9.4537, 0.00002, 0.0005, lev = 200),
        fx("USD", "SEK", Category.EXOTIC, 9.8912, 0.00002, 0.0005, lev = 200),
        fx("USD", "PLN", Category.EXOTIC, 3.9598, 0.00002, 0.0006, lev = 200),
        fx("USD", "THB", Category.EXOTIC, 33.55, 0.00002, 0.0008, lev = 200, digits = 3),
        fx("USD", "INR", Category.EXOTIC, 93.91, 0.00001, 0.0005, lev = 200, digits = 3),

        // Minors
        fx("EUR", "GBP", Category.MINORS, 0.8347, 0.000012, 0.00015),
        fx("EUR", "JPY", Category.MINORS, 176.34, 0.000016, 0.00015),
        fx("EUR", "CHF", Category.MINORS, 0.914, 0.000012, 0.00018),
        fx("EUR", "AUD", Category.MINORS, 1.6222, 0.000016, 0.0002),
        fx("EUR", "CAD", Category.MINORS, 1.5743, 0.000014, 0.0002),
        fx("EUR", "NZD", Category.MINORS, 2.0088, 0.000018, 0.00025),
        fx("GBP", "JPY", Category.MINORS, 211.547, 0.000018, 0.00018),
        fx("GBP", "CHF", Category.MINORS, 1.0953, 0.000015, 0.0002),
        fx("GBP", "AUD", Category.MINORS, 1.9444, 0.000018, 0.00022),
        fx("GBP", "CAD", Category.MINORS, 1.8861, 0.000016, 0.00022),
        fx("GBP", "NZD", Category.MINORS, 2.4067, 0.00002, 0.00028),
        fx("AUD", "JPY", Category.MINORS, 108.794, 0.000018, 0.00018),
        fx("AUD", "NZD", Category.MINORS, 1.2375, 0.000012, 0.0002),
        fx("AUD", "CAD", Category.MINORS, 0.9691, 0.000014, 0.0002),
        fx("AUD", "CHF", Category.MINORS, 0.5631, 0.000015, 0.00022),
        fx("NZD", "JPY", Category.MINORS, 87.877, 0.000018, 0.0002),
        fx("CAD", "JPY", Category.MINORS, 112.123, 0.000016, 0.0002),
        fx("CHF", "JPY", Category.MINORS, 193.104, 0.000016, 0.0002),
        fx("CAD", "CHF", Category.MINORS, 0.5806, 0.000014, 0.00022),
    )

    private val bySymbol = all.associateBy { it.symbol }

    operator fun get(symbol: String): Instrument = bySymbol.getValue(symbol)

    fun find(symbol: String): Instrument? = bySymbol[symbol]

    val byPopularity: List<Instrument> = all.sortedBy { it.popularity }
}
