package de.medizininformatik_initiative.process.data_sharing.service.merge;

import java.util.List;

import org.hl7.fhir.r4.model.Endpoint;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.Task;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.ServiceTask;
import dev.dsf.bpe.v2.client.dsf.DelayStrategy;
import dev.dsf.bpe.v2.client.dsf.DsfClient;
import dev.dsf.bpe.v2.constants.CodeSystems;
import dev.dsf.bpe.v2.constants.NamingSystems;
import dev.dsf.bpe.v2.service.EndpointProvider;
import dev.dsf.bpe.v2.variables.Target;
import dev.dsf.bpe.v2.variables.Targets;
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

		if (!variables.getBoolean(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_MERGE_RECEIVE_ERROR_EXISTS))
			addStartTaskOutputReceivedDataSet(api, variables, dicIdentifier.getValue());

		removeOrganizationFromTargets(dicIdentifier.getValue(), variables);
	}

	private Identifier getDicOrganizationIdentifier(Task task)
	{
		return task.getRequester().getIdentifier();
	}

	private Endpoint getDicEndpoint(EndpointProvider endpointProvider, Identifier organizationIdentifier)
	{
		return endpointProvider.getEndpoint(NamingSystems.OrganizationIdentifier.withValue(
				ConstantsBase.NAMINGSYSTEM_DSF_ORGANIZATION_IDENTIFIER_MEDICAL_INFORMATICS_INITIATIVE_CONSORTIUM),
				organizationIdentifier, CodeSystems.OrganizationRole.dic())
				.orElseThrow(() -> new RuntimeException("Could not find Endpoint of organization '"
						+ ConstantsBase.NAMINGSYSTEM_DSF_ORGANIZATION_IDENTIFIER_MEDICAL_INFORMATICS_INITIATIVE_CONSORTIUM
						+ "|" + organizationIdentifier.getValue() + "'"));
	}

	private Target createTarget(Variables variables, Identifier dicIdentifier, Endpoint dicEndpoint)
	{
		String dicEndpointIdentifier = extractEndpointIdentifier(dicEndpoint);
		return variables.createTarget(dicIdentifier.getValue(), dicEndpointIdentifier, dicEndpoint.getAddress());
	}

	private String extractEndpointIdentifier(Endpoint endpoint)
	{
		return endpoint.getIdentifier().stream().filter(i -> NamingSystems.EndpointIdentifier.SID.equals(i.getSystem()))
				.map(Identifier::getValue).findFirst().orElseThrow(() -> new RuntimeException(
						"Endpoint '" + endpoint.getId() + "' does not contain any identifier"));
	}

	private void addStartTaskOutputReceivedDataSet(ProcessPluginApi api, Variables variables,
			String organizationIdentifier)
	{
		DsfClient client = api.getDsfClientProvider().getLocal();

		// read latest version, variable is outdated if a previous attempt updated the Task but was rolled back
		Task task = client.read(Task.class, variables.getStartTask().getIdElement().getIdPart());

		boolean outputExists = task.getOutput().stream()
				.filter(o -> o.getType().hasCoding(ConstantsDataSharing.CODESYSTEM_DATA_SHARING,
						ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DATA_SET_RECEIVED))
				.filter(o -> o.getValue() instanceof Reference)
				.anyMatch(o -> organizationIdentifier.equals(((Reference) o.getValue()).getIdentifier().getValue()));

		if (!outputExists)
		{
			task.addOutput()
					.setValue(new Reference()
							.setIdentifier(NamingSystems.OrganizationIdentifier.withValue(organizationIdentifier))
							.setType(ResourceType.Organization.name()))
					.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
					.setVersion(api.getProcessPluginDefinition().getResourceVersion())
					.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DATA_SET_RECEIVED);

			task = client.withRetry(ConstantsBase.DSF_CLIENT_RETRY_6_TIMES,
					DelayStrategy.constant(ConstantsBase.DSF_CLIENT_RETRY_INTERVAL_5MIN)).update(task);
		}

		variables.updateTask(task);
	}

	private void removeOrganizationFromTargets(String organizationIdentifier, Variables variables)
	{
		List<Target> targets = variables.getTargets().getEntries();
		List<Target> targetsWithoutReceivedIdentifier = targets.stream()
				.filter(t -> !organizationIdentifier.equals(t.getOrganizationIdentifierValue())).toList();
		Targets newTargets = variables.createTargets(targetsWithoutReceivedIdentifier);
		variables.setTargets(newTargets);
	}
}
