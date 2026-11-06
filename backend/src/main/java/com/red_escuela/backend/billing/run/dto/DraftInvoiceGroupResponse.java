package com.red_escuela.backend.billing.run.dto;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.run.domain.DraftInvoiceGroup;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceEnrollmentType;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.SignedPercentage;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record DraftInvoiceGroupResponse(MonthlyInvoiceEnrollmentType enrollmentType, String name, FeeType feeType, int draftCount, Money draftTotal,
                                        Money previousPeriodIssuedTotal, Money difference, SignedPercentage differencePercentage,
                                        @Schema(description = "Ids de los borradores del grupo. Una factura con líneas en más de un grupo aparece en cada uno de ellos: el cliente debe deduplicar los ids al combinar grupos. Aprobar o descartar cualquiera de esos ids afecta a la factura completa, y volver a enviarla en otro grupo da 409 DRAFT_NOT_PENDING.") List<Long> invoiceIds) {

    public static DraftInvoiceGroupResponse of(DraftInvoiceGroup group) {
        return new DraftInvoiceGroupResponse(group.key().scope(), group.key().name(), group.key().feeType(), group.draftCount(), group.draftTotal(),
                group.findPreviousIssuedTotal().orElse(null), group.findDifferenceFromPreviousPeriod().orElse(null),
                group.findPercentageDifferenceFromPreviousPeriod().orElse(null), group.invoiceIds());
    }
}
