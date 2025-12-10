package com.a.prestamos.service;

import com.a.prestamos.model.dto.comprobante.ComprobanteDto;

import java.util.List;

public interface IComprobanteService {
    List<ComprobanteDto> buscarPorCuota(Long cuotaId);
}
