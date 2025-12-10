package com.a.prestamos.service.impl;

import com.a.prestamos.model.dao.ComprobanteDao;
import com.a.prestamos.model.dto.comprobante.ComprobanteDto;
import com.a.prestamos.model.entity.Comprobante;
import com.a.prestamos.service.IComprobanteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ComprobanteServiceImpl implements IComprobanteService {
    @Autowired
    private ComprobanteDao comprobanteDao;

    @Override
    @Transactional(readOnly = true) // <--- Aquí es donde debe ir
    public List<ComprobanteDto> buscarPorCuota(Long cuotaId) {
        List<Comprobante> lista = comprobanteDao.findByPaymentInstallmentId(cuotaId);

        // Convertimos AQUÍ, dentro de la transacción segura
        return lista.stream()
                .map(ComprobanteDto::fromEntity)
                .toList();
    }
}
