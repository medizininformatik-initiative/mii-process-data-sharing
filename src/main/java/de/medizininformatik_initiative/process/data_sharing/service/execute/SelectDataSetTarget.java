package de.medizininformatik_initiative.process.data_sharing.service.execute;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Task;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.constants.CodeSystems;
import dev.dsf.bpe.v1.constants.NamingSystems;
import dev.dsf.bpe.v1.variables.Target;
import dev.dsf.bpe.v1.variables.Variables;

public class SelectDataSetTarget extends AbstractServiceDelegate
{

	public SelectDataSetTarget(ProcessPluginApi api)
	{
		super(api);
	}

	@Override
	protected void doExecute(DelegateExecution execution, Variables variables)
	{
		String dmsIdentifier = getDmsIdentifier(variables);
		String correlationKey = getCorrelationKey(variables);
		Target target = getDmsTarget(dmsIdentifier, correlationKey, variables);

		variables.setTarget(target);
	}

	private String getDmsIdentifier(Variables variables)
	{
		return variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DMS_IDENTIFIER);

	}

	private String getCorrelationKey(Variables variables)
	{
		Task task = variables.getStartTask();
		return api.getTaskHelper().getFirstInputParameterStringValue(task, CodeSystems.BpmnMessage.correlationKey())
				.orElseThrow(
						() -> new RuntimeException("No correlation key found in Task with id '" + task.getId() + "'"));
	}

	private Target getDmsTarget(String identifier, String correlationKey, Variables variables)
	{
		return api.getEndpointProvider().getEndpoint(NamingSystems.OrganizationIdentifier.withValue(
				ConstantsBase.NAMINGSYSTEM_DSF_ORGANIZATION_IDENTIFIER_MEDICAL_INFORMATICS_INITIATIVE_CONSORTIUM),
				NamingSystems.OrganizationIdentifier.withValue(identifier),
				new Coding().setSystem(ConstantsBase.CODESYSTEM_DSF_ORGANIZATION_ROLE)
						.setCode(ConstantsBase.CODESYSTEM_DSF_ORGANIZATION_ROLE_VALUE_DMS))
				.map(e -> variables.createTarget(identifier, e.getIdentifierFirstRep().getValue(), e.getAddress(),
						correlationKey))
				.orElseThrow(
						() -> new RuntimeException("No Endpoint of DMS with identifier '" + identifier + "' found"));
	}
}
