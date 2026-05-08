package de.medizininformatik_initiative.process.data_sharing.service.coordinate;

import org.hl7.fhir.r4.model.Endpoint;
import org.hl7.fhir.r4.model.Identifier;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.ServiceTask;
import dev.dsf.bpe.v2.constants.CodeSystems;
import dev.dsf.bpe.v2.constants.NamingSystems;
import dev.dsf.bpe.v2.variables.Target;
import dev.dsf.bpe.v2.variables.Variables;

public class SelectDmsTarget implements ServiceTask
{
	public SelectDmsTarget()
	{
	}

	@Override
	public void execute(ProcessPluginApi api, Variables variables)
	{
		String dmsIdentifier = getDmsIdentifier(variables);
		Target target = getDmsTarget(api, variables, dmsIdentifier);
		variables.setTarget(target);
	}

	private String getDmsIdentifier(Variables variables)
	{
		return variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DMS_IDENTIFIER);

	}

	private Target getDmsTarget(ProcessPluginApi api, Variables variables, String dmsIdentifier)
	{
		Endpoint endpoint = getEndpoint(api, dmsIdentifier);
		return variables.createTarget(dmsIdentifier, getEndpointIdentifierValue(endpoint), endpoint.getAddress());
	}

	private Endpoint getEndpoint(ProcessPluginApi api, String organizationIdentifier)
	{
		return api.getEndpointProvider().getEndpoint(NamingSystems.OrganizationIdentifier.withValue(
				ConstantsBase.NAMINGSYSTEM_DSF_ORGANIZATION_IDENTIFIER_MEDICAL_INFORMATICS_INITIATIVE_CONSORTIUM),
				NamingSystems.OrganizationIdentifier.withValue(organizationIdentifier),
				CodeSystems.OrganizationRole.dms())
				.orElseThrow(() -> new RuntimeException(
						"Could not find Endpoint of organization '" + organizationIdentifier + "'"));
	}

	private String getEndpointIdentifierValue(Endpoint endpoint)
	{
		return endpoint.getIdentifier().stream().filter(i -> NamingSystems.EndpointIdentifier.SID.equals(i.getSystem()))
				.findFirst().map(Identifier::getValue).orElseThrow(() -> new RuntimeException(
						"Endpoint '" + endpoint.getId() + "' does not contain any identifier"));
	}
}
