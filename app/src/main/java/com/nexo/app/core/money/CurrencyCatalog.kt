package com.nexo.app.core.money

import com.nexo.app.core.model.SymbolPosition
import java.util.Currency
import java.util.Locale

/** One built-in currency: [mainSymbol] is used when it is the main currency, [subSymbol] when it is a sub-currency. */
data class CatalogCurrency(
    val code: String,
    val mainSymbol: String,
    val subSymbol: String,
    val position: SymbolPosition,
    val decimals: Int,
) {
    fun displayName(locale: Locale = Locale.getDefault()): String {
        val name = runCatching { Currency.getInstance(code).getDisplayName(locale) }.getOrNull()
        return if (name.isNullOrBlank() || name == code) code else "$code - $name"
    }
}

object CurrencyCatalog {
    private val TABLE = """
USD|$|US$|P|2
EUR|€|€|P|2
GBP|£|£|P|2
JPY|¥|JP¥|P|0
CNY|¥|CN¥|P|2
KRW|₩|₩|P|0
INR|₹|₹|P|2
LKR|Rs|LKR|P|2
AUD|$|A$|P|2
CAD|$|CA$|P|2
NZD|$|NZ$|P|2
SGD|$|S$|P|2
HKD|$|HK$|P|2
TWD|$|NT$|P|2
CHF|CHF|CHF|P|2
SEK|kr|SEK|S|2
NOK|kr|NOK|S|2
DKK|kr|DKK|S|2
ISK|kr|ISK|S|0
PLN|zł|PLN|S|2
CZK|Kč|CZK|S|2
HUF|Ft|HUF|S|2
RON|lei|RON|S|2
BGN|лв|BGN|S|2
RUB|₽|RUB|S|2
UAH|₴|UAH|S|2
BYN|Br|BYN|S|2
TRY|₺|TRY|P|2
ILS|₪|₪|P|2
SAR|SR|SAR|P|2
QAR|QR|QAR|P|2
AED|AED|AED|P|2
KWD|KD|KWD|P|3
BHD|BD|BHD|P|3
OMR|OMR|OMR|P|3
JOD|JD|JOD|P|3
IQD|IQD|IQD|P|3
LYD|LD|LYD|P|3
TND|DT|TND|P|3
EGP|E£|EGP|P|2
MAD|MAD|MAD|S|2
DZD|DA|DZD|P|2
ZAR|R|ZAR|P|2
NGN|₦|NGN|P|2
KES|KSh|KES|P|2
GHS|GH₵|GHS|P|2
TZS|TSh|TZS|P|2
UGX|USh|UGX|P|0
ETB|Br|ETB|P|2
XOF|CFA|XOF|S|0
XAF|FCFA|XAF|S|0
MUR|Rs|MUR|P|2
PKR|Rs|PKR|P|2
BDT|৳|BDT|P|2
NPR|Rs|NPR|P|2
MVR|Rf|MVR|P|2
AFN|؋|AFN|P|2
IRR|IRR|IRR|P|2
KZT|₸|KZT|P|2
UZS|soʻm|UZS|S|2
AZN|₼|AZN|P|2
GEL|₾|GEL|P|2
AMD|֏|AMD|P|2
MNT|₮|MNT|P|2
THB|฿|฿|P|2
VND|₫|₫|S|0
IDR|Rp|Rp|P|2
MYR|RM|RM|P|2
PHP|₱|₱|P|2
MMK|K|MMK|P|2
KHR|៛|KHR|P|2
LAK|₭|LAK|P|2
BND|B$|BND|P|2
MOP|MOP$|MOP|P|2
MXN|$|MX$|P|2
BRL|R$|R$|P|2
ARS|$|AR$|P|2
CLP|$|CL$|P|0
COP|$|CO$|P|2
PEN|S/|PEN|P|2
UYU|${'$'}U|UYU|P|2
PYG|₲|PYG|P|0
BOB|Bs|BOB|P|2
VES|Bs.S|VES|P|2
CRC|₡|CRC|P|2
GTQ|Q|GTQ|P|2
HNL|L|HNL|P|2
NIO|C$|NIO|P|2
PAB|B/.|PAB|P|2
DOP|RD$|DOP|P|2
JMD|J$|JMD|P|2
TTD|TT$|TTD|P|2
BSD|B$|BSD|P|2
BBD|Bds$|BBD|P|2
XCD|EC$|XCD|P|2
HTG|G|HTG|P|2
CUP|${'$'}MN|CUP|P|2
FJD|FJ$|FJD|P|2
PGK|K|PGK|P|2
XPF|₣|XPF|S|0
RSD|din|RSD|S|2
MKD|ден|MKD|S|2
ALL|L|ALL|S|2
BAM|KM|BAM|S|2
MDL|L|MDL|S|2
KGS|сом|KGS|S|2
TJS|SM|TJS|S|2
TMT|m|TMT|S|2
SYP|£S|SYP|P|2
LBP|L£|LBP|P|2
YER|YER|YER|P|2
SDG|SDG|SDG|P|2
SOS|Sh|SOS|P|2
RWF|FRw|RWF|P|0
BIF|FBu|BIF|P|0
MWK|MK|MWK|P|2
ZMW|ZK|ZMW|P|2
BWP|P|BWP|P|2
NAD|N$|NAD|P|2
SZL|E|SZL|P|2
LSL|L|LSL|P|2
MZN|MT|MZN|P|2
AOA|Kz|AOA|P|2
CDF|FC|CDF|P|2
GMD|D|GMD|P|2
SLE|Le|SLE|P|2
LRD|L$|LRD|P|2
GNF|FG|GNF|P|0
MGA|Ar|MGA|P|2
SCR|SR|SCR|P|2
CVE|Esc|CVE|P|2
BTN|Nu.|BTN|P|2
WST|WS$|WST|P|2
TOP|T$|TOP|P|2
VUV|VT|VUV|P|0
SBD|SI$|SBD|P|2
KYD|CI$|KYD|P|2
BMD|BD$|BMD|P|2
AWG|ƒ|AWG|P|2
ANG|ƒ|ANG|P|2
SRD|$|SRD|P|2
GYD|G$|GYD|P|2
BZD|BZ$|BZD|P|2
ERN|Nfk|ERN|P|2
DJF|Fdj|DJF|P|0
KMF|CF|KMF|P|0
MRU|UM|MRU|P|2
STN|Db|STN|P|2
"""

    val all: List<CatalogCurrency> by lazy {
        TABLE.trim().lines().map { line ->
            val p = line.split('|')
            CatalogCurrency(
                code = p[0],
                mainSymbol = p[1],
                subSymbol = p[2],
                position = if (p[3] == "S") SymbolPosition.SUFFIX else SymbolPosition.PREFIX,
                decimals = p[4].toInt(),
            )
        }.sortedBy { it.code }
    }

    private val byCode: Map<String, CatalogCurrency> by lazy { all.associateBy { it.code } }

    fun find(code: String?): CatalogCurrency? = code?.let { byCode[it.uppercase(Locale.ROOT)] }

    /** Currency of the device region, falling back to USD. */
    fun forLocale(locale: Locale = Locale.getDefault()): CatalogCurrency {
        val code = runCatching { Currency.getInstance(locale).currencyCode }.getOrNull()
        return find(code) ?: byCode.getValue("USD")
    }
}
