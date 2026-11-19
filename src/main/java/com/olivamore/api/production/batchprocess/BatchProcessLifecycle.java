package com.olivamore.api.production.batchprocess;

import com.olivamore.api.identity.CurrentUser;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.JsonNodeFactory;

import java.math.BigDecimal;

/** Parti tarafinin asamalara dokundugu yerler: olusturmada kantar tartimi ve parti durumunun yeniden hesabi. */
@Component
public class BatchProcessLifecycle {

	private final BatchProcessService service;

	BatchProcessLifecycle(BatchProcessService service) {
		this.service = service;
	}

	/** Olusturmada girilen kilo kantar tartimi olarak islenir: partinin zeytin materyali olusur. */
	public void completeWeighing(long processId, BigDecimal quantity, CurrentUser actor) {
		service.completeOnCreation(processId, JsonNodeFactory.instance.objectNode().put("quantity", quantity), actor);
	}

	/** Silinecek teslimatin kantar tartimi, geri alinabiliyorsa, geri alinir. */
	public void undoOriginWeighing(long batchId) {
		service.undoOriginWeighing(batchId);
	}

	/** Grubu degisen teslimat ya da yeni grup icin. */
	public void syncBatchStatus(long batchId) {
		service.syncBatchStatus(batchId);
	}

}
