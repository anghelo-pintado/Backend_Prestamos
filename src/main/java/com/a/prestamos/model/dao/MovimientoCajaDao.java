package com.a.prestamos.model.dao;

import com.a.prestamos.model.entity.MovimientoCaja;
import com.a.prestamos.model.entity.enums.TipoMovimientoCaja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface MovimientoCajaDao extends JpaRepository<MovimientoCaja, Long> {

    /**
     * Obtener todos los movimientos de una caja específica ordenados por fecha
     */
    List<MovimientoCaja> findByCajaIdOrderByFechaDesc(Long cajaId);

    /**
     * CALCULO GENÉRICO: Sumar montos por tipo de movimiento.
     * Al pasar el Enum 'tipo' como parámetro, evitamos errores de conversión
     * entre String y Enum en la base de datos.
     */
    @Query("SELECT COALESCE(SUM(m.monto), 0) FROM MovimientoCaja m " +
            "WHERE m.caja.id = :cajaId AND m.tipo = :tipo")
    BigDecimal calcularTotalPorTipo(@Param("cajaId") Long cajaId,
                                    @Param("tipo") TipoMovimientoCaja tipo);
}