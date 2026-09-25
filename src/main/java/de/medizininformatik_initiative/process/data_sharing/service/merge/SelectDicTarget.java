package de.medizininformatik_initiative.process.data_sharing.service.merge;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Endpoint;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Task;

import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.constants.NamingSystems;
import dev.dsf.bpe.v1.variables.Target;
import dev.dsf.bpe.v1.variables.Variables;

public class SelectDicTarget extends AbstractServiceDelegate
{
	public SelectDicTarget(ProcessPluginApi api)
	{
		super(api);
	}

	@Override
	protected void doExecute(DelegateExecution execution, Variables variables)
	{
		Task task = variables.getLatestTask();
		Identifier dicIdentifier = getDicOrganizationIdentifier(task);
		Endpoint dicEndpoint = getDicEndpoint(dicIdentifier);
		Target dicTarget = createTarget(variables, dicIdentifier, dicEndpoint);

		variables.setTarget(dicTarget);

		if (isLocalOrganization(dicIdentifier))
			completeLatestTask(variables);
	}

	private Identifier getDicOrganizationIdentifier(Task task)
	{
		return task.getRequester().getIdentifier();
	}

	private Endpoint getDicEndpoint(Identifier dicIdentifier)
	{
		Identifier parentIdentifier = NamingSystems.OrganizationIdentifier.withValue(
				ConstantsBase.NAMINGSYSTEM_DSF_ORGANIZATION_IDENTIFIER_MEDICAL_INFORMATICS_INITIATIVE_CONSORTIUM);
		Coding role = new Coding().setSystem(ConstantsBase.CODESYSTEM_DSF_ORGANIZATION_ROLE)
				.setCode(ConstantsBase.CODESYSTEM_DSF_ORGANIZATION_ROLE_VALUE_DIC);
		return api.getEndpointProvider().getEndpoint(parentIdentifier, dicIdentifier, role)
				.orElseThrow(() -> new RuntimeException(
						"Could not find default endpoint of organization '" + dicIdentifier.getValue() + "'"));
	}

	private Target createTarget(Variables variables, Identifier dicIdentifier, Endpoint dicEndpoint)
	{
		String dicEndpointIdentifier = extractEndpointIdentifier(dicEndpoint);
		return variables.createTarget(dicIdentifier.getValue(), dicEndpointIdentifier, dicEndpoint.getAddress());
	}

	private boolean isLocalOrganization(Identifier organizationIdentifier)
	{
		return api.getOrganizationProvider().getLocalOrganizationIdentifierValue()
				.map(organizationIdentifier.getValue()::equals).orElse(false);
	}

	/*
	 * If the DMS is also the DIC, the receipt is delivered to the execute process on the same DSF instance, which
	 * immediately continues and permanently deletes the DocumentReference referenced by the received Task. The end
	 * listener of this subprocess would complete the received Task only afterwards and fail the update due to the
	 * unresolvable reference. Completing the Task before sending the receipt prevents this, the end listener skips
	 * Tasks not in-progress.
	 */
	private void completeLatestTask(Variables variables)
	{
		Task task = variables.getLatestTask();

		if (task == null || !Task.TaskStatus.INPROGRESS.equals(task.getStatus()))
			return;

		task.setStatus(Task.TaskStatus.COMPLETED);
		task = api.getFhirWebserviceClientProvider().getLocalWebserviceClient()
				.withRetry(ConstantsBase.DSF_CLIENT_RETRY_6_TIMES, ConstantsBase.DSF_CLIENT_RETRY_INTERVAL_5MIN)
				.update(task);

		variables.updateTask(task);
	}

	private String extractEndpointIdentifier(Endpoint endpoint)
	{
		return endpoint.getIdentifier().stream().filter(i -> NamingSystems.EndpointIdentifier.SID.equals(i.getSystem()))
				.map(Identifier::getValue).findFirst()
				.orElseThrow(() -> new RuntimeException("Endpoint with id '" + endpoint.getId()
						+ "' is missing identifier with system '" + NamingSystems.EndpointIdentifier.SID + "'"));
	}
}
