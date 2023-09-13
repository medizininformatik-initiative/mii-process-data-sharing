package de.medizininformatik_initiative.process.data_sharing.service.merge;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.constants.CodeSystems;
import dev.dsf.bpe.v1.variables.Target;
import dev.dsf.bpe.v1.variables.Targets;
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
		Target dicTarget = variables.getTarget();
		String correlationKey = extractCorrelationKey(variables.getLatestTask());
		Target reinsertTarget = variables.createTarget(dicTarget.getOrganizationIdentifierValue(),
				dicTarget.getEndpointIdentifierValue(), dicTarget.getEndpointUrl(), correlationKey);

		logger.warn(
				"Error during data-set receive - reinserting target for organization '{}' with correlation-key '{}' and data-sharing project '{}' referenced in Task with id '{}'",
				reinsertTarget.getOrganizationIdentifierValue(), correlationKey,
				variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER),
				variables.getLatestTask().getId());

		Targets targets = variables.getTargets();
		targets.getEntries().add(reinsertTarget);
		variables.setTargets(targets);
	}

	private String extractCorrelationKey(Task task)
	{
		return api.getTaskHelper()
				.getFirstInputParameterValue(task, CodeSystems.BpmnMessage.correlationKey(), StringType.class)
				.orElseThrow(() -> new RuntimeException("CorrelationKey is missing")).getValue();
	}
}
