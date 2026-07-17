package de.medizininformatik_initiative.process.data_sharing.service.execute;

import org.hl7.fhir.r4.model.Task;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.ServiceTask;
import dev.dsf.bpe.v2.constants.CodeSystems;
import dev.dsf.bpe.v2.constants.NamingSystems;
import dev.dsf.bpe.v2.variables.Target;
import dev.dsf.bpe.v2.variables.Variables;

public class SelectDataSetTarget implements ServiceTask
{

	public SelectDataSetTarget()
	{
	}

	@Override
	public void execute(ProcessPluginApi api, Variables variables)
	{
		String dmsIdentifier = getDmsIdentifier(variables);
		String correlationKey = getCorrelationKey(api, variables);
		Target target = getDmsTarget(api, dmsIdentifier, correlationKey, variables);

		variables.setTarget(target);
	}

	private String getDmsIdentifier(Variables variables)
	{
		return variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DMS_IDENTIFIER);

	}

	private String getCorrelationKey(ProcessPluginApi api, Variables variables)
	{
		Task task = variables.getStartTask();
		return api.getTaskHelper().getFirstInputParameterStringValue(task, CodeSystems.BpmnMessage.correlationKey())
				.orElseThrow(() -> new RuntimeException("Task.input:correlation-key missing"));
	}

	private Target getDmsTarget(ProcessPluginApi api, String organizationIdentifier, String correlationKey,
			Variables variables)
	{
		return api.getEndpointProvider().getEndpoint(NamingSystems.OrganizationIdentifier.withValue(
				ConstantsBase.NAMINGSYSTEM_DSF_ORGANIZATION_IDENTIFIER_MEDICAL_INFORMATICS_INITIATIVE_CONSORTIUM),
				NamingSystems.OrganizationIdentifier.withValue(organizationIdentifier),
				CodeSystems.OrganizationRole.dms())
				.map(e -> variables.createTarget(organizationIdentifier, e.getIdentifierFirstRep().getValue(),
						e.getAddress(), correlationKey))
				.orElseThrow(() -> new RuntimeException("Could not find Endpoint of organization '"
						+ ConstantsBase.NAMINGSYSTEM_DSF_ORGANIZATION_IDENTIFIER_MEDICAL_INFORMATICS_INITIATIVE_CONSORTIUM
						+ "|" + organizationIdentifier + "'"));
	}
}
