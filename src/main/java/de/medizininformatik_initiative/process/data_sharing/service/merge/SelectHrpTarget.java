package de.medizininformatik_initiative.process.data_sharing.service.merge;

import java.util.Objects;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Identifier;

import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.constants.NamingSystems;
import dev.dsf.bpe.v1.variables.Target;
import dev.dsf.bpe.v1.variables.Variables;

public class SelectHrpTarget extends AbstractServiceDelegate
{
	public SelectHrpTarget(ProcessPluginApi api)
	{
		super(api);
	}

	@Override
	protected void doExecute(DelegateExecution execution, Variables variables)
	{
		String hrp = getHrpIdentifier();
		Target target = getHrpTarget(hrp, variables);

		variables.setTarget(target);
	}

	private String getHrpIdentifier()
	{
		return api.getOrganizationProvider().getOrganizations(NamingSystems.OrganizationIdentifier.withValue(
				ConstantsBase.NAMINGSYSTEM_DSF_ORGANIZATION_IDENTIFIER_MEDICAL_INFORMATICS_INITIATIVE_CONSORTIUM),
				new Coding().setSystem(ConstantsBase.CODESYSTEM_DSF_ORGANIZATION_ROLE)
						.setCode(ConstantsBase.CODESYSTEM_DSF_ORGANIZATION_ROLE_VALUE_HRP))
				.stream().flatMap(o -> o.getIdentifier().stream()).filter(Objects::nonNull).map(Identifier::getValue)
				.findFirst().orElseThrow(() -> new RuntimeException("No organization with role HRP found"));
	}

	private Target getHrpTarget(String identifier, Variables variables)
	{
		return api.getEndpointProvider().getEndpoint(NamingSystems.OrganizationIdentifier.withValue(
				ConstantsBase.NAMINGSYSTEM_DSF_ORGANIZATION_IDENTIFIER_MEDICAL_INFORMATICS_INITIATIVE_CONSORTIUM),
				NamingSystems.OrganizationIdentifier.withValue(identifier),
				new Coding().setSystem(ConstantsBase.CODESYSTEM_DSF_ORGANIZATION_ROLE)
						.setCode(ConstantsBase.CODESYSTEM_DSF_ORGANIZATION_ROLE_VALUE_HRP))
				.map(e -> variables.createTarget(identifier, e.getIdentifierFirstRep().getValue(), e.getAddress()))
				.orElseThrow(() -> new RuntimeException(
						"No Endpoint of organization with with identifier '" + identifier + "' found"));
	}
}
