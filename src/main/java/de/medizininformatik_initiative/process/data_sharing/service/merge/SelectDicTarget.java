package de.medizininformatik_initiative.process.data_sharing.service.merge;

import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Endpoint;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Task;

import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.ServiceTask;
import dev.dsf.bpe.v2.constants.NamingSystems;
import dev.dsf.bpe.v2.service.EndpointProvider;
import dev.dsf.bpe.v2.variables.Target;
import dev.dsf.bpe.v2.variables.Variables;

public class SelectDicTarget implements ServiceTask
{
	public SelectDicTarget()
	{
	}

	@Override
	public void execute(ProcessPluginApi api, Variables variables)
	{
		Task task = variables.getLatestTask();
		Identifier dicIdentifier = getDicOrganizationIdentifier(task);
		Endpoint dicEndpoint = getDicEndpoint(api.getEndpointProvider(), dicIdentifier);
		Target dicTarget = createTarget(variables, dicIdentifier, dicEndpoint);

		variables.setTarget(dicTarget);
	}

	private Identifier getDicOrganizationIdentifier(Task task)
	{
		return task.getRequester().getIdentifier();
	}

	private Endpoint getDicEndpoint(EndpointProvider endpointProvider, Identifier dicIdentifier)
	{
		Identifier parentIdentifier = NamingSystems.OrganizationIdentifier.withValue(
				ConstantsBase.NAMINGSYSTEM_DSF_ORGANIZATION_IDENTIFIER_MEDICAL_INFORMATICS_INITIATIVE_CONSORTIUM);
		Coding role = new Coding().setSystem(ConstantsBase.CODESYSTEM_DSF_ORGANIZATION_ROLE)
				.setCode(ConstantsBase.CODESYSTEM_DSF_ORGANIZATION_ROLE_VALUE_DIC);
		return endpointProvider.getEndpoint(parentIdentifier, dicIdentifier, role)
				.orElseThrow(() -> new RuntimeException(
						"Could not find default endpoint of organization '" + dicIdentifier.getValue() + "'"));
	}

	private Target createTarget(Variables variables, Identifier dicIdentifier, Endpoint dicEndpoint)
	{
		String dicEndpointIdentifier = extractEndpointIdentifier(dicEndpoint);
		return variables.createTarget(dicIdentifier.getValue(), dicEndpointIdentifier, dicEndpoint.getAddress());
	}

	private String extractEndpointIdentifier(Endpoint endpoint)
	{
		return endpoint.getIdentifier().stream().filter(i -> NamingSystems.EndpointIdentifier.SID.equals(i.getSystem()))
				.map(Identifier::getValue).findFirst()
				.orElseThrow(() -> new RuntimeException("Endpoint with id '" + endpoint.getId()
						+ "' is missing identifier with system '" + NamingSystems.EndpointIdentifier.SID + "'"));
	}
}
