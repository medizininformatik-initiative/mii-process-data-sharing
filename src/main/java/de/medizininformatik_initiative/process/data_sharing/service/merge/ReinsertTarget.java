package de.medizininformatik_initiative.process.data_sharing.service.merge;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Endpoint;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.constants.CodeSystems;
import dev.dsf.bpe.v1.constants.NamingSystems;
import dev.dsf.bpe.v1.variables.Target;
import dev.dsf.bpe.v1.variables.Variables;

public class ReinsertTarget extends AbstractServiceDelegate
{
	private static final Logger logger = LoggerFactory.getLogger(ReinsertTarget.class);

	public ReinsertTarget(ProcessPluginApi api)
	{
		super(api);
	}

	@Override
	protected void doExecute(DelegateExecution delegateExecution, Variables variables)
	{
		Task latestTask = variables.getLatestTask();
		String dicIdentifier = extractDicIdentifier(latestTask);
		Endpoint dicEndpoint = getDicEndpoint(dicIdentifier);
		String correlationKey = extractCorrelationKey(latestTask);

		Target reinsertTarget = variables.createTarget(dicIdentifier, dicEndpoint.getIdentifierFirstRep().getValue(),
				dicEndpoint.getAddress(), correlationKey);

		logger.warn(
				"Error during data-set receive - reinserting target for organization '{}' with correlation-key '{}' and data-sharing project '{}' referenced in Task with id '{}'",
				reinsertTarget.getOrganizationIdentifierValue(), correlationKey,
				variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER),
				latestTask.getId());

		variables.setTarget(reinsertTarget);

		variables.setString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_MERGE_RECEIVE_ERROR, null);
		variables.setString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_MERGE_RECEIVE_ERROR_MESSAGE,
				null);
		variables.setBoolean(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_MERGE_RECEIVE_ERROR_EXISTS,
				false);
	}

	private String extractDicIdentifier(Task task)
	{
		return task.getRequester().getIdentifier().getValue();
	}

	private Endpoint getDicEndpoint(String dicIdentifier)
	{
		return api.getEndpointProvider().getEndpoint(NamingSystems.OrganizationIdentifier.withValue(
				ConstantsBase.NAMINGSYSTEM_DSF_ORGANIZATION_IDENTIFIER_MEDICAL_INFORMATICS_INITIATIVE_CONSORTIUM),
				NamingSystems.OrganizationIdentifier.withValue(dicIdentifier),
				new Coding().setSystem(ConstantsBase.CODESYSTEM_DSF_ORGANIZATION_ROLE)
						.setCode(ConstantsBase.CODESYSTEM_DSF_ORGANIZATION_ROLE_VALUE_DIC))
				.orElseThrow(() -> new RuntimeException("No endpoint for dic with identifier '" + dicIdentifier + "'"));
	}

	private String extractCorrelationKey(Task task)
	{
		return api.getTaskHelper()
				.getFirstInputParameterValue(task, CodeSystems.BpmnMessage.correlationKey(), StringType.class)
				.orElseThrow(() -> new RuntimeException("CorrelationKey is missing")).getValue();
	}
}
