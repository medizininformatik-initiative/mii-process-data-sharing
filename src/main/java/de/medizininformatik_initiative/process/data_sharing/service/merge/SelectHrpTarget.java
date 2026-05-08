package de.medizininformatik_initiative.process.data_sharing.service.merge;

import java.util.Objects;

import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Identifier;

import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.ServiceTask;
import dev.dsf.bpe.v2.constants.NamingSystems;
import dev.dsf.bpe.v2.service.EndpointProvider;
import dev.dsf.bpe.v2.service.OrganizationProvider;
import dev.dsf.bpe.v2.variables.Target;
import dev.dsf.bpe.v2.variables.Variables;

public class SelectHrpTarget implements ServiceTask
{
	public SelectHrpTarget()
	{
	}

	@Override
	public void execute(ProcessPluginApi api, Variables variables)
	{
		String hrp = getHrpIdentifier(api.getOrganizationProvider());
		Target target = getHrpTarget(api.getEndpointProvider(), hrp, variables);

		variables.setTarget(target);
	}

	private String getHrpIdentifier(OrganizationProvider organizationProvider)
	{
		return organizationProvider.getOrganizations(NamingSystems.OrganizationIdentifier.withValue(
				ConstantsBase.NAMINGSYSTEM_DSF_ORGANIZATION_IDENTIFIER_MEDICAL_INFORMATICS_INITIATIVE_CONSORTIUM),
				new Coding().setSystem(ConstantsBase.CODESYSTEM_DSF_ORGANIZATION_ROLE)
						.setCode(ConstantsBase.CODESYSTEM_DSF_ORGANIZATION_ROLE_VALUE_HRP))
				.stream().flatMap(o -> o.getIdentifier().stream()).filter(Objects::nonNull).map(Identifier::getValue)
				.findFirst().orElseThrow(() -> new RuntimeException("No organization with role HRP found"));
	}

	private Target getHrpTarget(EndpointProvider endpointProvider, String identifier, Variables variables)
	{
		return endpointProvider.getEndpoint(NamingSystems.OrganizationIdentifier.withValue(
				ConstantsBase.NAMINGSYSTEM_DSF_ORGANIZATION_IDENTIFIER_MEDICAL_INFORMATICS_INITIATIVE_CONSORTIUM),
				NamingSystems.OrganizationIdentifier.withValue(identifier),
				new Coding().setSystem(ConstantsBase.CODESYSTEM_DSF_ORGANIZATION_ROLE)
						.setCode(ConstantsBase.CODESYSTEM_DSF_ORGANIZATION_ROLE_VALUE_HRP))
				.map(e -> variables.createTarget(identifier, e.getIdentifierFirstRep().getValue(), e.getAddress()))
				.orElseThrow(() -> new RuntimeException(
						"No Endpoint of organization with with identifier '" + identifier + "' found"));
	}
}
