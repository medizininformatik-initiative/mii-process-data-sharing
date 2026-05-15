package de.medizininformatik_initiative.process.data_sharing.service.merge;

import java.util.Objects;

import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Task;

import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.ServiceTask;
import dev.dsf.bpe.v2.client.dsf.DelayStrategy;
import dev.dsf.bpe.v2.constants.CodeSystems;
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
		String hrpIdentifier = getHrpIdentifier(api.getOrganizationProvider());
		Target target = getHrpTarget(api.getEndpointProvider(), hrpIdentifier, variables);

		variables.setTarget(target);

		Task startTask = variables.getStartTask();
		Task latestTask = variables.getLatestTask();
		// latestTask not updated automatically in consolidate case
		updateLatestTaskIfNotStartTask(api, startTask, latestTask);
	}

	private String getHrpIdentifier(OrganizationProvider organizationProvider)
	{
		return organizationProvider.getOrganizations(NamingSystems.OrganizationIdentifier.withValue(
				ConstantsBase.NAMINGSYSTEM_DSF_ORGANIZATION_IDENTIFIER_MEDICAL_INFORMATICS_INITIATIVE_CONSORTIUM),
				CodeSystems.OrganizationRole.hrp()).stream().flatMap(o -> o.getIdentifier().stream())
				.filter(Objects::nonNull).map(Identifier::getValue).findFirst()
				.orElseThrow(() -> new RuntimeException("No organization with role HRP found"));
	}

	private Target getHrpTarget(EndpointProvider endpointProvider, String organizationIdentifier, Variables variables)
	{
		return endpointProvider.getEndpoint(NamingSystems.OrganizationIdentifier.withValue(
				ConstantsBase.NAMINGSYSTEM_DSF_ORGANIZATION_IDENTIFIER_MEDICAL_INFORMATICS_INITIATIVE_CONSORTIUM),
				NamingSystems.OrganizationIdentifier.withValue(organizationIdentifier),
				CodeSystems.OrganizationRole.hrp())
				.map(e -> variables.createTarget(organizationIdentifier, e.getIdentifierFirstRep().getValue(),
						e.getAddress()))
				.orElseThrow(() -> new RuntimeException("Could not find Endpoint of organization '"
						+ ConstantsBase.NAMINGSYSTEM_DSF_ORGANIZATION_IDENTIFIER_MEDICAL_INFORMATICS_INITIATIVE_CONSORTIUM
						+ "|" + organizationIdentifier + "'"));
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
