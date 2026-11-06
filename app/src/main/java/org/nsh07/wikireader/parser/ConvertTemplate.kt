package org.nsh07.wikireader.parser

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong

/*
 * Renderer for Wikipedia's {{convert}} / {{cvt}} template.
 *
 * The old implementation stopped after the *input* unit ("100 and(-)"), which lost the unit,
 * the converted value and every named option. This converter understands the usual template
 * grammar:
 *
 *   {{convert|100|and(-)|360|mg|grain|frac=2|abbr=off}}
 *   -> 100 and 360 milligrams (1+1⁄2–5+1⁄2 grains)
 *
 * It is deliberately forgiving: any value it cannot parse falls back to readable plain text
 * instead of leaking template syntax into the article. Unknown units are printed as-is.
 */
internal object ConvertTemplate {

    private enum class Dimension { LENGTH, MASS, VOLUME, AREA, SPEED, TIME, ENERGY, POWER, PRESSURE, FORCE, TEMPERATURE, FREQUENCY, ANGLE }

    private class Unit(
        val dimension: Dimension,
        val factor: Double,
        val short: String,
        val singular: String,
        val plural: String = singular + "s",
        /** Only used by temperature: base = value * factor + offset. */
        val offset: Double = 0.0
    )

    // ---------------------------------------------------------------------------------------
    // Unit table. The factor converts a value to the base unit of its dimension.
    // ---------------------------------------------------------------------------------------
    private val units: Map<String, Unit> = buildMap {
        fun put(
            aliases: List<String>,
            dimension: Dimension,
            factor: Double,
            short: String,
            singular: String,
            plural: String = singular + "s",
            offset: Double = 0.0
        ) {
            val unit = Unit(dimension, factor, short, singular, plural, offset)
            aliases.forEach { put(it.lowercase(), unit) }
        }

        // Length (base: metre)
        put(listOf("m", "metre", "metres", "meter", "meters"), Dimension.LENGTH, 1.0, "m", "metre", "metres")
        put(listOf("cm", "centimetre", "centimetres", "centimeter", "centimeters"), Dimension.LENGTH, 0.01, "cm", "centimetre", "centimetres")
        put(listOf("mm", "millimetre", "millimetres", "millimeter", "millimeters"), Dimension.LENGTH, 0.001, "mm", "millimetre", "millimetres")
        put(listOf("km", "kilometre", "kilometres", "kilometer", "kilometers"), Dimension.LENGTH, 1000.0, "km", "kilometre", "kilometres")
        put(listOf("um", "µm", "micrometre", "micrometres", "micrometer", "micrometers"), Dimension.LENGTH, 1e-6, "µm", "micrometre", "micrometres")
        put(listOf("nm", "nanometre", "nanometres", "nanometer", "nanometers"), Dimension.LENGTH, 1e-9, "nm", "nanometre", "nanometres")
        put(listOf("in", "inch", "inches"), Dimension.LENGTH, 0.0254, "in", "inch", "inches")
        put(listOf("ft", "foot", "feet"), Dimension.LENGTH, 0.3048, "ft", "foot", "feet")
        put(listOf("yd", "yard", "yards"), Dimension.LENGTH, 0.9144, "yd", "yard", "yards")
        put(listOf("mi", "mile", "miles"), Dimension.LENGTH, 1609.344, "mi", "mile", "miles")
        put(listOf("nmi", "nautical mile", "nautical miles"), Dimension.LENGTH, 1852.0, "nmi", "nautical mile", "nautical miles")
        put(listOf("furlong", "furlongs"), Dimension.LENGTH, 201.168, "furlong", "furlong", "furlongs")
        put(listOf("chain", "chains"), Dimension.LENGTH, 20.1168, "chain", "chain", "chains")
        put(listOf("angstrom", "angstroms", "Å"), Dimension.LENGTH, 1e-10, "Å", "ångström", "ångströms")

        // Mass (base: kilogram)
        put(listOf("kg", "kilogram", "kilograms"), Dimension.MASS, 1.0, "kg", "kilogram", "kilograms")
        put(listOf("g", "gram", "grams"), Dimension.MASS, 0.001, "g", "gram", "grams")
        put(listOf("mg", "milligram", "milligrams"), Dimension.MASS, 1e-6, "mg", "milligram", "milligrams")
        put(listOf("ug", "µg", "microgram", "micrograms"), Dimension.MASS, 1e-9, "µg", "microgram", "micrograms")
        put(listOf("ng", "nanogram", "nanograms"), Dimension.MASS, 1e-12, "ng", "nanogram", "nanograms")
        put(listOf("t", "tonne", "tonnes", "metric ton", "metric tons"), Dimension.MASS, 1000.0, "t", "tonne", "tonnes")
        put(listOf("lb", "lbs", "pound", "pounds"), Dimension.MASS, 0.45359237, "lb", "pound", "pounds")
        put(listOf("oz", "ounce", "ounces"), Dimension.MASS, 0.028349523125, "oz", "ounce", "ounces")
        put(listOf("st", "stone", "stones"), Dimension.MASS, 6.35029318, "st", "stone", "stone")
        put(listOf("gr", "grain", "grains"), Dimension.MASS, 6.479891e-5, "gr", "grain", "grains")
        put(listOf("carat", "carats", "ct"), Dimension.MASS, 0.0002, "ct", "carat", "carats")

        // Volume (base: litre)
        put(listOf("l", "litre", "litres", "liter", "liters"), Dimension.VOLUME, 1.0, "L", "litre", "litres")
        put(listOf("ml", "millilitre", "millilitres", "milliliter", "milliliters"), Dimension.VOLUME, 0.001, "mL", "millilitre", "millilitres")
        put(listOf("cl", "centilitre", "centilitres", "centiliter", "centiliters"), Dimension.VOLUME, 0.01, "cL", "centilitre", "centilitres")
        put(listOf("dl", "decilitre", "decilitres", "deciliter", "deciliters"), Dimension.VOLUME, 0.1, "dL", "decilitre", "decilitres")
        put(listOf("m3", "m³", "cubic metre", "cubic metres", "cubic meter", "cubic meters"), Dimension.VOLUME, 1000.0, "m³", "cubic metre", "cubic metres")
        put(listOf("cm3", "cm³", "cc", "cubic centimetre", "cubic centimetres"), Dimension.VOLUME, 0.001, "cm³", "cubic centimetre", "cubic centimetres")
        put(listOf("cuft", "cu ft", "cubic foot", "cubic feet"), Dimension.VOLUME, 28.316846592, "cu ft", "cubic foot", "cubic feet")
        put(listOf("cuin", "cu in", "cubic inch", "cubic inches"), Dimension.VOLUME, 0.016387064, "cu in", "cubic inch", "cubic inches")
        put(listOf("usgal", "us gal", "gallon", "gallons", "gal"), Dimension.VOLUME, 3.785411784, "US gal", "US gallon", "US gallons")
        put(listOf("impgal", "imp gal", "imperial gallon", "imperial gallons"), Dimension.VOLUME, 4.54609, "imp gal", "imperial gallon", "imperial gallons")
        put(listOf("usqt", "us qt", "quart", "quarts", "qt"), Dimension.VOLUME, 0.946352946, "US qt", "US quart", "US quarts")
        put(listOf("imppt", "imp pt", "imperial pint", "imperial pints"), Dimension.VOLUME, 0.56826125, "imp pt", "imperial pint", "imperial pints")
        put(listOf("uspt", "us pt", "pint", "pints", "pt"), Dimension.VOLUME, 0.473176473, "US pt", "US pint", "US pints")
        put(listOf("usfloz", "us fl oz", "fluid ounce", "fluid ounces", "floz"), Dimension.VOLUME, 0.0295735295625, "US fl oz", "US fluid ounce", "US fluid ounces")
        put(listOf("impfloz", "imp fl oz", "imperial fluid ounce", "imperial fluid ounces"), Dimension.VOLUME, 0.0284130625, "imp fl oz", "imperial fluid ounce", "imperial fluid ounces")
        put(listOf("bbl", "barrel", "barrels"), Dimension.VOLUME, 158.987294928, "bbl", "barrel", "barrels")

        // Area (base: square metre)
        put(listOf("m2", "m²", "sqm", "square metre", "square metres", "square meter", "square meters"), Dimension.AREA, 1.0, "m²", "square metre", "square metres")
        put(listOf("cm2", "cm²", "square centimetre", "square centimetres"), Dimension.AREA, 1e-4, "cm²", "square centimetre", "square centimetres")
        put(listOf("km2", "km²", "square kilometre", "square kilometres", "square kilometer", "square kilometers"), Dimension.AREA, 1e6, "km²", "square kilometre", "square kilometres")
        put(listOf("ha", "hectare", "hectares"), Dimension.AREA, 10_000.0, "ha", "hectare", "hectares")
        put(listOf("acre", "acres"), Dimension.AREA, 4046.8564224, "acre", "acre", "acres")
        put(listOf("sqft", "sq ft", "square foot", "square feet"), Dimension.AREA, 0.09290304, "sq ft", "square foot", "square feet")
        put(listOf("sqin", "sq in", "square inch", "square inches"), Dimension.AREA, 0.00064516, "sq in", "square inch", "square inches")
        put(listOf("sqyd", "sq yd", "square yard", "square yards"), Dimension.AREA, 0.83612736, "sq yd", "square yard", "square yards")
        put(listOf("sqmi", "sq mi", "square mile", "square miles"), Dimension.AREA, 2_589_988.110336, "sq mi", "square mile", "square miles")

        // Speed (base: metre/second)
        put(listOf("m/s", "mps", "metre per second", "metres per second"), Dimension.SPEED, 1.0, "m/s", "metre per second", "metres per second")
        put(listOf("km/h", "kmh", "kph", "kilometre per hour", "kilometres per hour"), Dimension.SPEED, 1.0 / 3.6, "km/h", "kilometre per hour", "kilometres per hour")
        put(listOf("mph", "mi/h", "mile per hour", "miles per hour"), Dimension.SPEED, 0.44704, "mph", "mile per hour", "miles per hour")
        put(listOf("kn", "knot", "knots"), Dimension.SPEED, 1852.0 / 3600.0, "kn", "knot", "knots")
        put(listOf("ft/s", "fps", "foot per second", "feet per second"), Dimension.SPEED, 0.3048, "ft/s", "foot per second", "feet per second")

        // Time (base: second)
        put(listOf("s", "sec", "second", "seconds"), Dimension.TIME, 1.0, "s", "second", "seconds")
        put(listOf("min", "minute", "minutes"), Dimension.TIME, 60.0, "min", "minute", "minutes")
        put(listOf("h", "hr", "hour", "hours"), Dimension.TIME, 3600.0, "h", "hour", "hours")
        put(listOf("day", "days", "d"), Dimension.TIME, 86_400.0, "day", "day", "days")
        put(listOf("week", "weeks", "wk"), Dimension.TIME, 604_800.0, "week", "week", "weeks")

        // Energy (base: joule)
        put(listOf("j", "joule", "joules"), Dimension.ENERGY, 1.0, "J", "joule", "joules")
        put(listOf("kj", "kilojoule", "kilojoules"), Dimension.ENERGY, 1000.0, "kJ", "kilojoule", "kilojoules")
        put(listOf("mj", "megajoule", "megajoules"), Dimension.ENERGY, 1e6, "MJ", "megajoule", "megajoules")
        put(listOf("cal", "calorie", "calories"), Dimension.ENERGY, 4.184, "cal", "calorie", "calories")
        put(listOf("kcal", "kilocalorie", "kilocalories"), Dimension.ENERGY, 4184.0, "kcal", "kilocalorie", "kilocalories")
        put(listOf("wh", "watt-hour", "watt-hours"), Dimension.ENERGY, 3600.0, "Wh", "watt-hour", "watt-hours")
        put(listOf("kwh", "kilowatt-hour", "kilowatt-hours"), Dimension.ENERGY, 3.6e6, "kWh", "kilowatt-hour", "kilowatt-hours")
        put(listOf("btu", "btus"), Dimension.ENERGY, 1055.05585262, "BTU", "British thermal unit", "British thermal units")
        put(listOf("ev", "electronvolt", "electronvolts"), Dimension.ENERGY, 1.602176634e-19, "eV", "electronvolt", "electronvolts")

        // Power (base: watt)
        put(listOf("w", "watt", "watts"), Dimension.POWER, 1.0, "W", "watt", "watts")
        put(listOf("kw", "kilowatt", "kilowatts"), Dimension.POWER, 1000.0, "kW", "kilowatt", "kilowatts")
        put(listOf("mw", "megawatt", "megawatts"), Dimension.POWER, 1e6, "MW", "megawatt", "megawatts")
        put(listOf("hp", "horsepower"), Dimension.POWER, 745.6998715823, "hp", "horsepower", "horsepower")
        put(listOf("ps", "metric horsepower"), Dimension.POWER, 735.49875, "PS", "metric horsepower", "metric horsepower")

        // Pressure (base: pascal)
        put(listOf("pa", "pascal", "pascals"), Dimension.PRESSURE, 1.0, "Pa", "pascal", "pascals")
        put(listOf("kpa", "kilopascal", "kilopascals"), Dimension.PRESSURE, 1000.0, "kPa", "kilopascal", "kilopascals")
        put(listOf("mpa", "megapascal", "megapascals"), Dimension.PRESSURE, 1e6, "MPa", "megapascal", "megapascals")
        put(listOf("bar", "bars"), Dimension.PRESSURE, 100_000.0, "bar", "bar", "bar")
        put(listOf("mbar", "millibar", "millibars"), Dimension.PRESSURE, 100.0, "mbar", "millibar", "millibars")
        put(listOf("atm", "atmosphere", "atmospheres"), Dimension.PRESSURE, 101_325.0, "atm", "atmosphere", "atmospheres")
        put(listOf("psi"), Dimension.PRESSURE, 6894.757293168, "psi", "pound per square inch", "pounds per square inch")
        put(listOf("mmhg", "millimetre of mercury", "millimetres of mercury"), Dimension.PRESSURE, 133.322387415, "mmHg", "millimetre of mercury", "millimetres of mercury")
        put(listOf("torr"), Dimension.PRESSURE, 133.322368421, "Torr", "torr", "torr")
        put(listOf("inhg", "inHg"), Dimension.PRESSURE, 3386.389, "inHg", "inch of mercury", "inches of mercury")

        // Force (base: newton)
        put(listOf("n", "newton", "newtons"), Dimension.FORCE, 1.0, "N", "newton", "newtons")
        put(listOf("kilonewton", "kilonewtons", "knewton"), Dimension.FORCE, 1000.0, "kN", "kilonewton", "kilonewtons")
        put(listOf("lbf", "pound-force"), Dimension.FORCE, 4.4482216152605, "lbf", "pound-force", "pounds-force")
        put(listOf("kgf", "kilogram-force"), Dimension.FORCE, 9.80665, "kgf", "kilogram-force", "kilograms-force")
        put(listOf("dyn", "dyne", "dynes"), Dimension.FORCE, 1e-5, "dyn", "dyne", "dynes")

        // Temperature (base: kelvin)
        put(listOf("c", "°c", "celsius", "degree celsius", "degrees celsius"), Dimension.TEMPERATURE, 1.0, "°C", "degree Celsius", "degrees Celsius", 273.15)
        put(listOf("f", "°f", "fahrenheit", "degree fahrenheit", "degrees fahrenheit"), Dimension.TEMPERATURE, 5.0 / 9.0, "°F", "degree Fahrenheit", "degrees Fahrenheit", 255.3722222222222)
        put(listOf("k", "kelvin", "kelvins"), Dimension.TEMPERATURE, 1.0, "K", "kelvin", "kelvins")
        put(listOf("r", "rankine"), Dimension.TEMPERATURE, 5.0 / 9.0, "°R", "degree Rankine", "degrees Rankine")

        // Frequency (base: hertz)
        put(listOf("hz", "hertz"), Dimension.FREQUENCY, 1.0, "Hz", "hertz", "hertz")
        put(listOf("khz", "kilohertz"), Dimension.FREQUENCY, 1000.0, "kHz", "kilohertz", "kilohertz")
        put(listOf("mhz", "megahertz"), Dimension.FREQUENCY, 1e6, "MHz", "megahertz", "megahertz")
        put(listOf("ghz", "gigahertz"), Dimension.FREQUENCY, 1e9, "GHz", "gigahertz", "gigahertz")

        // Angle
        put(listOf("deg", "°", "degree", "degrees"), Dimension.ANGLE, 1.0, "°", "degree", "degrees")
        put(listOf("rad", "radian", "radians"), Dimension.ANGLE, 180.0 / Math.PI, "rad", "radian", "radians")
    }

    private val rangeSeparators = setOf("-", "–", "—", "to", "and", "and(-)", "by", "x", "×", "*", "or")
    private val namedKeys = setOf(
        "abbr", "frac", "sp", "adj", "disp", "order", "round", "sigfig", "precision", "lk",
        "u", "ul", "lang", "sortable", "debug", "0", "1", "2", "3", "4", "5"
    )

    /** Renders the template body (everything after the first `|`). */
    fun render(body: String): String {
        val parts = splitTopLevel(body, '|')
        if (parts.isEmpty()) return ""

        val positional = ArrayList<String>()
        val named = HashMap<String, String>()
        for (raw in parts) {
            val part = raw.trim()
            val eq = part.indexOf('=')
            if (eq > 0) {
                val key = part.substring(0, eq).trim().lowercase()
                if (key in namedKeys || key.toIntOrNull() != null) {
                    // Numbered parameters are just positional arguments in source order.
                    if (key.toIntOrNull() == null) named[key] = part.substring(eq + 1).trim()
                    else positional.add(part.substring(eq + 1).trim())
                    continue
                }
            }
            positional.add(part)
        }
        if (positional.isEmpty()) return ""

        // Grammar: value1 [separator value2] unit1 [unit2] [unit3 ...]
        var value1 = positional[0]
        var value2: String? = null
        var index = 1
        var separator: String? = null
        if (positional.size > 1 && positional[1].lowercase() in rangeSeparators) {
            separator = positional[1]
            value2 = positional.getOrNull(2)
            index = 3
        }
        val unit1Raw = positional.getOrNull(index)?.trim().orEmpty()
        val unit2Raw = positional.getOrNull(index + 1)?.trim().orEmpty()

        if (unit1Raw.isBlank()) return renderPlain(positional.joinToString(" "))
        val unit1 = lookupUnit(unit1Raw)
        val unit2 = lookupUnit(unit2Raw)

        // Unknown input unit: keep the source readable rather than dropping the numbers.
        if (unit1 == null) {
            val primary = buildString {
                if (value2 != null) {
                    append(value1).append(separatorText(separator)).append(value2)
                } else append(value1)
                append(' ').append(unit1Raw)
            }
            return primary
        }

        val abbr = when (named["abbr"]?.lowercase()) {
            "off", "none", "false", "no" -> false
            else -> true
        }
        val fracDenominator = named["frac"]?.toIntOrNull()?.takeIf { it in 2..64 }
        val roundTo = named["round"]?.toIntOrNull()?.takeIf { it in 0..8 }
        val sigfigOverride = named["sigfig"]?.toIntOrNull()?.takeIf { it in 1..6 }
        val disp = named["disp"]?.lowercase().orEmpty()
        val flip = named["order"]?.lowercase() == "flip"

        val number1 = parseNumber(value1)
        val number2 = value2?.let { parseNumber(it) }
        val converted = if (unit2 != null && unit1.dimension == unit2.dimension) {
            val sig = sigfigOverride ?: maxOf(2, minOf(4, significantDigits(value1)))
            fun convertText(value: String, number: Double?): String? {
                if (number == null) return null
                val target = convert(number, unit1, unit2)
                return formatNumber(target, sig, fracDenominator, roundTo)
            }
            val a = convertText(value1, number1) ?: return fallback(value1, value2, value1, unit1Raw, separator)
            val b = if (number2 != null) convertText(value2, number2) ?: a else null
            buildString {
                append(a)
                if (b != null) append(ndash).append(b)
                append(' ')
                append(unitLabel(unit2, abbr, isPlural(number1, number2, fracDenominator)))
            }
        } else null

        val inputPlural = isPlural(number1, number2, fracDenominator)
        val primary = buildString {
            if (number1 != null) append(formatInputNumber(number1, value1))
            else append(value1)
            if (number2 != null) {
                append(separatorText(separator))
                append(formatInputNumber(number2, value2!!))
            } else if (value2 != null) {
                append(separatorText(separator)).append(value2)
            }
            append(' ')
            append(unitLabel(unit1, abbr, inputPlural))
        }

        if (converted == null) return primary
        return when {
            disp == "out" || disp == "only" -> converted
            disp == "unit" || disp == "in" -> primary
            flip -> "$converted ($primary)"
            else -> "$primary ($converted)"
        }
    }

    /** Plain readable fallback (no conversion), used when the units do not match. */
    private fun fallback(
        value1: String,
        value2: String?,
        number1Text: String,
        unit1Raw: String,
        separator: String?
    ): String = buildString {
        append(number1Text)
        if (value2 != null) append(separatorText(separator)).append(value2)
        append(' ').append(unit1Raw)
    }

    private fun renderPlain(text: String): String = text.replace('_', ' ')

    private fun lookupUnit(raw: String): Unit? {
        val key = raw.trim().lowercase()
        if (key.isEmpty()) return null
        units[key]?.let { return it }
        // English plurals: "miles" -> "mile", "lbs" -> "lb".
        if (key.length > 2 && key.endsWith("s")) {
            units[key.dropLast(1)]?.let { return it }
        }
        return units[key.replace(" ", "")]
    }

    private fun convert(value: Double, from: Unit, to: Unit): Double {
        if (from.dimension == Dimension.TEMPERATURE) {
            val kelvin = value * from.factor + from.offset
            return (kelvin - to.offset) / to.factor
        }
        return value * from.factor / to.factor
    }

    private fun isPlural(v1: Double?, v2: Double?, fracDenominator: Int?): Boolean {
        if (v2 != null) return true
        val v = v1 ?: return true
        return abs(v - 1.0) > 1e-9
    }

    private fun unitLabel(unit: Unit, abbr: Boolean, plural: Boolean): String =
        if (abbr) unit.short else if (plural) unit.plural else unit.singular

    private fun separatorText(separator: String?): String = when (separator?.lowercase()) {
        null -> " "
        "-", "–", "—" -> ndash
        "to" -> " to "
        "and" -> " and "
        "and(-)" -> " and "
        "by" -> " by "
        "x", "×", "*" -> " × "
        "or" -> " or "
        else -> " $separator "
    }

    // ---------------------------------------------------------------------------------------
    // Number parsing / formatting
    // ---------------------------------------------------------------------------------------

    /** Parses `100`, `1.5`, `1,000`, `1+1⁄2`, `1 1/2`, `1/2` and `1.2e3`. */
    internal fun parseNumber(raw: String): Double? {
        var s = raw.trim().replace(",", "").replace("\u00A0", " ").replace("−", "-")
        if (s.isEmpty()) return null
        s = s.removePrefix("+")
        // Scientific notation
        Regex("^([-+]?\\d*\\.?\\d+)\\s*[eE]\\s*([-+]?\\d+)$").matchEntire(s)?.let { m ->
            val mantissa = m.groupValues[1].toDoubleOrNull() ?: return null
            val exponent = m.groupValues[2].toIntOrNull() ?: return null
            return mantissa * 10.0.pow(exponent)
        }
        // Mixed fraction: "1+1/2" or "1 1/2"
        Regex("^([-+]?\\d+)\\s*[+ ]\\s*(\\d+)\\s*[/⁄]\\s*(\\d+)$").matchEntire(s)?.let { m ->
            val whole = m.groupValues[1].toDouble()
            val numerator = m.groupValues[2].toDouble()
            val denominator = m.groupValues[3].toDouble()
            if (denominator == 0.0) return null
            return if (whole < 0) whole - numerator / denominator else whole + numerator / denominator
        }
        Regex("^([-+]?\\d+)\\s*[/⁄]\\s*(\\d+)$").matchEntire(s)?.let { m ->
            val denominator = m.groupValues[2].toDouble()
            if (denominator == 0.0) return null
            return m.groupValues[1].toDouble() / denominator
        }
        return s.toDoubleOrNull()
    }

    /** Significant digits of an input token, clamped to 1..6. */
    private fun significantDigits(raw: String): Int {
        val digits = raw.trim().removePrefix("+").removePrefix("-").replace(",", "")
            .filter { it.isDigit() }
            .trimStart('0')
        return digits.length.coerceIn(1, 6)
    }

    internal fun formatNumber(
        value: Double,
        significant: Int,
        fracDenominator: Int?,
        roundTo: Int?
    ): String {
        if (value.isNaN() || value.isInfinite()) return ""
        if (fracDenominator != null && fracDenominator > 1) {
            return mixedFraction(value, fracDenominator)
        }
        if (roundTo != null) {
            val factor = 10.0.pow(roundTo)
            val rounded = (value * factor).roundToLong() / factor
            return trimNumber(rounded, roundTo)
        }
        val sig = significant.coerceIn(1, 10)
        if (value == 0.0) return "0"
        val exponent = floor(log10(abs(value))).toInt()
        val decimals = (sig - 1 - exponent).coerceIn(0, 12)
        val factor = 10.0.pow(decimals)
        val rounded = (value * factor).roundToLong() / factor
        if (abs(rounded) >= 1e9 || (abs(rounded) < 1e-4 && rounded != 0.0)) {
            return scientific(rounded, sig)
        }
        return trimNumber(rounded, decimals)
    }

    private fun trimNumber(value: Double, decimals: Int): String {
        val text = String.format(java.util.Locale.US, "%.${decimals.coerceIn(0, 12)}f", value)
        return if (text.contains('.')) text.trimEnd('0').trimEnd('.') else text
    }

    private fun scientific(value: Double, sig: Int): String {
        if (value == 0.0) return "0"
        val exponent = floor(log10(abs(value))).toInt()
        val mantissa = value / 10.0.pow(exponent)
        val mantissaText = trimNumber(mantissa, (sig - 1).coerceIn(0, 6))
        return "$mantissaText×${powerOfTen(exponent)}"
    }

    /** "10⁴" for 4, "10⁻³" for -3. Used by {{val}} as well. */
    internal fun powerOfTen(exponent: Int): String {
        val sup = exponent.toString().map { c ->
            when (c) {
                '-' -> '⁻'
                '0' -> '⁰'; '1' -> '¹'; '2' -> '²'; '3' -> '³'; '4' -> '⁴'
                '5' -> '⁵'; '6' -> '⁶'; '7' -> '⁷'; '8' -> '⁸'; '9' -> '⁹'
                else -> c
            }
        }.joinToString("")
        return "10$sup"
    }

    /** "1+1⁄2", "5+1⁄2", "3", "1⁄2". */
    internal fun mixedFraction(value: Double, denominator: Int): String {
        val negative = value < 0
        val absValue = abs(value)
        val total = (absValue * denominator).roundToLong()
        val whole = total / denominator
        val numerator = total % denominator
        val text = when {
            numerator == 0L -> whole.toString()
            whole == 0L -> "$numerator$fractionSlash$denominator"
            else -> "$whole+$numerator$fractionSlash$denominator"
        }
        return if (negative) "-$text" else text
    }

    /** Keeps the user's original number spelling when it is already a plain number. */
    private fun formatInputNumber(value: Double, original: String): String {
        val trimmed = original.trim()
        if (trimmed.matches(Regex("^[-+]?\\d+(?:[.,]\\d+)?(?:[eE][-+]?\\d+)?$"))) {
            return trimmed.replace(',', '.')
        }
        // Fractions keep the familiar "1+1⁄2" form.
        val mixed = Regex("^([-+]?\\d+)\\s*[+ ]\\s*(\\d+)\\s*[/⁄]\\s*(\\d+)$").matchEntire(trimmed)
        if (mixed != null) return "${mixed.groupValues[1]}+${mixed.groupValues[2]}$fractionSlash${mixed.groupValues[3]}"
        return trimmed
    }

    /**
     * Splits template arguments on [delimiter], ignoring delimiters nested inside
     * `{{...}}`, `[[...]]` or `<...>`.
     */
    internal fun splitTopLevel(text: String, delimiter: Char): List<String> {
        val out = ArrayList<String>()
        val current = StringBuilder()
        var braces = 0
        var brackets = 0
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '{' && text.getOrNull(i + 1) == '{' -> { braces++; current.append("{{"); i += 2; continue }
                c == '}' && text.getOrNull(i + 1) == '}' && braces > 0 -> { braces--; current.append("}}"); i += 2; continue }
                c == '[' && text.getOrNull(i + 1) == '[' -> { brackets++; current.append("[["); i += 2; continue }
                c == ']' && text.getOrNull(i + 1) == ']' && brackets > 0 -> { brackets--; current.append("]]"); i += 2; continue }
                c == delimiter && braces == 0 && brackets == 0 -> {
                    out.add(current.toString()); current.clear(); i++; continue
                }
            }
            current.append(c)
            i++
        }
        out.add(current.toString())
        return out
    }

    private const val ndash = "\u2013"
    private const val fractionSlash = "\u2044"
}
