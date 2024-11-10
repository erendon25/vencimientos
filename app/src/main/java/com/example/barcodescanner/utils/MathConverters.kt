package com.example.barcodescanner.util

import androidx.room.TypeConverter
import org.apache.commons.math3.stat.descriptive.AbstractUnivariateStatistic

class MathConverters {
    @TypeConverter
    fun fromStatistic(statistic: AbstractUnivariateStatistic): String {
        return statistic.toString() // Cambia esto por tu método de serialización real
    }

    @TypeConverter
    fun toStatistic(@Suppress("UNUSED_PARAMETER") data: String): AbstractUnivariateStatistic {
        throw UnsupportedOperationException("Deserialization not implemented")
    }
}