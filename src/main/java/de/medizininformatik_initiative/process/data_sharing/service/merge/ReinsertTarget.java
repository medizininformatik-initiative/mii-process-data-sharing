package de.medizininformatik_initiative.process.data_sharing.service.merge;

import org.hl7.fhir.r4.model.Endpoint;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.ServiceTask;
import dev.dsf.bpe.v2.client.dsf.DelayStrategy;
import dev.dsf.bpe.v2.constants.CodeSystems;
import dev.dsf.bpe.v2.constants.NamingSystems;
import dev.dsf.bpe.v2.service.EndpointProvider;
import dev.dsf.bpe.v2.service.TaskHelper;
import dev.dsf.bpe.v2.variables.Target;
import dev.dsf.bpe.v2.variables.Variables;

public class ReinsertTarget implements ServiceTask
{
	private static final Logger logger = LoggerFactory.getLogger(ReinsertTarget.class);

	public ReinsertTarget()
	{
	}

	@Override
	public void execute(ProcessPluginApi api, Variables variables)
	{
		Task startTask = variables.getStartTask();
		Task latestTask = variables.getLatestTask();
		String dicIdentifier = extractDicIdentifier(latestTask);
		Endpoint dicEndpoint = getDicEndpoint(api.getEndpointProvider(), dicIdentifier);
		String correlationKey = extractCorrelationKey(api.getTaskHelper(), latestTask);

		Target reinsertTarget = variables.createTarget(dicIdentifier, dicEndpoint.getIdentifierFirstRep().getValue(),
				dicEndpoint.getAddress(), correlationKey);

		logger.warn("Error during data-set receive" + ConstantsBase.EXCEPTION_MESSAGE_DIVIDER
				+ "reinserting target for organization '{}' with correlation-key '{}' and data-sharing project '{}' for Task '{}'",
				reinsertTarget.getOrganizationIdentifierValue(), correlationKey,
				variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER),
				api.getTaskHelper().getLocalVersionlessAbsoluteUrl(latestTask));

		variables.setTarget(reinsertTarget);

		// latestTask not updated automatically in error case
		updateLatestTaskIfNotStartTask(api, startTask, latestTask);

		variables.setBoolean(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_MERGE_RECEIVE_ERROR_EXISTS,
				false);
	}

	private String extractDicIdentifier(Task task)
	{
		return task.getRequester().getIdentifier().getValue();
	}

	private Endpoint getDicEndpoint(EndpointProvider endpointProvider, String organizationIdentifier)
	{
		return endpointProvider.getEndpoint(NamingSystems.OrganizationIdentifier.withValue(
				ConstantsBase.NAMINGSYSTEM_DSF_ORGANIZATION_IDENTIFIER_MEDICAL_INFORMATICS_INITIATIVE_CONSORTIUM),
				NamingSystems.OrganizationIdentifier.withValue(organizationIdentifier),
				CodeSystems.OrganizationRole.dic())
				.orElseThrow(() -> new RuntimeException("Could not find Endpoint of organization '"
						+ ConstantsBase.NAMINGSYSTEM_DSF_ORGANIZATION_IDENTIFIER_MEDICAL_INFORMATICS_INITIATIVE_CONSORTIUM
						+ "|" + organizationIdentifier + "'"));
	}

	private String extractCorrelationKey(TaskHelper helper, Task task)
	{
		return helper.getFirstInputParameterValue(task, CodeSystems.BpmnMessage.correlationKey(), StringType.class)
				.orElseThrow(() -> new RuntimeException("Task.input:correlation-key missing")).getValue();
	}

	private void updateLatestTaskIfNotStartTask(ProcessPluginApi api, Task startTask, Task latestTask)
	{
		if (latestTask != null && startTask != latestTask)
		{
			api.getDsfClientProvider().getLocal().withRetry(ConstantsBase.DSF_CLIENT_RETRY_6_TIMES,
					DelayStrategy.constant(ConstantsBase.DSF_CLIENT_RETRY_INTERVAL_5MIN)).update(latestTask);
		}
	}
}
