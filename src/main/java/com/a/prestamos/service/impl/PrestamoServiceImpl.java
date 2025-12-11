package com.a.prestamos.service.impl;

import com.a.prestamos.exception.prestamo.LoanCreationException;
import com.a.prestamos.exception.prestamo.ResourceNotFoundException;
import com.a.prestamos.model.dao.CuotaDao;
import com.a.prestamos.model.dao.PrestamoDao;
import com.a.prestamos.model.dto.prestamo.PrestamoRequest;
import com.a.prestamos.model.entity.Cliente;
import com.a.prestamos.model.entity.Cuota;
import com.a.prestamos.model.entity.Prestamo;
import com.a.prestamos.model.entity.enums.InstallmentState;
import com.a.prestamos.model.entity.enums.LoanState;
import com.a.prestamos.service.IClienteService;
import com.a.prestamos.service.IFinancialService;
import com.a.prestamos.service.IPrestamoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PrestamoServiceImpl implements IPrestamoService {
    private final PrestamoDao loanRepository;
    private final CuotaDao installmentRepository;
    private final IClienteService customerService;
    private final IFinancialService financialService;


    @Override
    @Transactional
    public Prestamo createLoan(PrestamoRequest request) {
        // 1. Validar regla de negocio: Un solo préstamo por cliente
//        if (loanRepository.existsByCustomerDocumentId(request.documentId())) {
//            throw new LoanCreationException("El cliente con DNI/RUC " + request.documentId() + " ya tiene un préstamo registrado.");
//        }

        boolean tieneDeudaActiva = loanRepository.existsByCustomerDocumentId_AndLoanState(
                request.documentId(),
                LoanState.ACTIVO // O cualquier estado que no sea PAGADO/RECHAZADO
        );

        if (tieneDeudaActiva) {
            throw new IllegalStateException("El cliente ya tiene un préstamo en curso. Debe cancelarlo primero.");
        }

        // 2. Verificar y obtener el cliente
        Cliente customer = customerService.verifyByDocumentId(request.documentId());
        customer.setPep(request.pep());
        customerService.save(customer); // Actualizar el estado PEP si es necesario

        // 3. Realizar cálculos financieros
        BigDecimal tem = financialService.calculateTem(request.teaAnnual());
        BigDecimal installmentAmount = financialService.calculateInstallmentAmount(request.principal(), tem, request.months());

        // 4. Construir la entidad Loan
        Prestamo loan = new Prestamo();
        loan.setCustomer(customer);
        loan.setPrincipal(request.principal());
        loan.setTeaAnnual(request.teaAnnual());
        loan.setMonths(request.months());
        loan.setStartDate(request.startDate() != null ? request.startDate() : LocalDate.now());
        loan.setInstallmentAmount(installmentAmount);

        // 5. Guardar el préstamo para obtener su ID
        Prestamo savedLoan = loanRepository.save(loan);

        // 6. Generar y guardar el cronograma de pagos
        List<Cuota> schedule = financialService.generateSchedule(savedLoan, tem);
        installmentRepository.saveAll(schedule);

        // 7. Registrar acción en auditoría (ejemplo básico)
        // auditService.logAction("SYSTEM", "CREACION_PRESTAMO", "ID Préstamo: " + savedLoan.getId());

        return savedLoan;
    }

    @Override
    @Transactional
    public Prestamo findLoanByDocumentId(String documentId) {
        Prestamo prestamo = loanRepository.findByCustomerDocumentIdAndLoanState(documentId, LoanState.ACTIVO)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró un préstamo para el DNI/RUC: " + documentId));

        // ================================
        // 🔥 ACTUALIZAR AUTOMÁTICAMENTE CUOTAS VENCIDAS
        // ================================
        LocalDate hoy = LocalDate.now();

        for (Cuota cuota : prestamo.getInstallments()) {
            // Sólo interesa si la cuota NO está pagada
            if (cuota.getBalance().compareTo(BigDecimal.ZERO) > 0) {

                // Si la fecha ya pasó → marcar como vencida
                if (hoy.isAfter(cuota.getDueDate())) {
                    cuota.setInstallmentState(InstallmentState.VENCIDO);
                } else {
                    // Si aún no está vencida pero tampoco está pagada → pendiente
                    if (cuota.getAmountPaid().compareTo(BigDecimal.ZERO) == 0) {
                        cuota.setInstallmentState(InstallmentState.PENDIENTE);
                    } else {
                        cuota.setInstallmentState(InstallmentState.PAGADO_PARCIAL);
                    }
                }
            }
        }

        // Guardar los cambios
        installmentRepository.saveAll(prestamo.getInstallments());

        return prestamo;
    }
}
