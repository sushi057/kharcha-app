package com.kharcha.data

import androidx.room.TypeConverter
import com.kharcha.parser.Currency
import com.kharcha.parser.Direction

class Converters {
    @TypeConverter
    fun fromCurrency(currency: Currency): String = currency.name

    @TypeConverter
    fun toCurrency(value: String): Currency = Currency.valueOf(value)

    @TypeConverter
    fun fromDirection(direction: Direction): String = direction.name

    @TypeConverter
    fun toDirection(value: String): Direction = Direction.valueOf(value)
}
