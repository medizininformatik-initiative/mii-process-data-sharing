package de.medizininformatik_initiative.process.data_sharing.message;

import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Task;

import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractTaskMessageSend;
import dev.dsf.fhir.client.FhirWebserviceClient;

public class SendStopExecuteDataSharing extends AbstractTaskMessageSend
{
	public SendStopExecuteDataSharing(ProcessPluginApi api)
	{
		super(api);
	}

	@Override
	protected IdType doSend(FhirWebserviceClient client, Task task)
	{
		return client.withMinimalReturn()
				.withRetry(ConstantsBase.DSF_CLIENT_RETRY_6_TIMES, ConstantsBase.DSF_CLIENT_RETRY_INTERVAL_5MIN)
				.create(task);
	}
}
