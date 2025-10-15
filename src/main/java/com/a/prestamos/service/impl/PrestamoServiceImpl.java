package com.a.prestamos.service.impl;

import com.a.prestamos.exception.prestamo.LoanCreationException;
import com.a.prestamos.exception.prestamo.ResourceNotFoundException;
import com.a.prestamos.model.dao.CuotaDao;
import com.a.prestamos.model.dao.PrestamoDao;
import com.a.prestamos.model.dto.prestamo.PrestamoRequest;
import com.a.prestamos.model.entity.Cliente;
import com.a.prestamos.model.entity.Cuota;
import com.a.prestamos.model.entity.Prestamo;
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
        if (loanRepository.existsByCustomerDni(request.dni())) {
            throw new LoanCreationException("El cliente con DNI " + request.dni() + " ya tiene un préstamo registrado.");
        }

        // 2. Verificar y obtener el cliente
        Cliente customer = customerService.verifyById(request.dni());
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
    @Transactional(readOnly = true)
    public Prestamo findLoanByDni(String dni) {
        return loanRepository.findByCustomerDni(dni)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró un préstamo para el DNI: " + dni));
    }
}
