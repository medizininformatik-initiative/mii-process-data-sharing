package de.medizininformatik_initiative.process.data_sharing.service.merge;

import java.util.List;
import java.util.stream.Collectors;

import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Task;
import org.hl7.fhir.r4.model.UrlType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.ServiceTask;
import dev.dsf.bpe.v2.constants.CodeSystems;
import dev.dsf.bpe.v2.constants.NamingSystems;
import dev.dsf.bpe.v2.service.EndpointProvider;
import dev.dsf.bpe.v2.service.TaskHelper;
import dev.dsf.bpe.v2.variables.Target;
import dev.dsf.bpe.v2.variables.Targets;
import dev.dsf.bpe.v2.variables.Variables;

public class PrepareMerging implements ServiceTask
{
	private static final Logger logger = LoggerFactory.getLogger(PrepareMerging.class);

	public PrepareMerging()
	{
	}

	@Override
	public void execute(ProcessPluginApi api, Variables variables)
	{
		Task task = variables.getStartTask();

		String projectIdentifier = getProjectIdentifier(api.getTaskHelper(), task);
		variables.setString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER, projectIdentifier);

		String contractUrl = getContractUrl(api.getTaskHelper(), task);
		variables.setString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_CONTRACT_URL, contractUrl);

		List<String> researcherIdentifiers = getResearcherIdentifiers(api.getTaskHelper(), task);
		variables.setStringList(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_RESEARCHER_IDENTIFIERS,
				researcherIdentifiers);

		List<Target> targetsList = getTargets(api.getTaskHelper(), task, api.getEndpointProvider(), variables);
		Targets targets = variables.createTargets(targetsList);
		variables.setTargets(targets);

		logger.info(
				"Starting data-set reception and merging of approved data sharing project [project-identifier: {}; contract-url: {}; researchers: {}; dic: {}; task-id: {}]",
				projectIdentifier, contractUrl, String.join(",", researcherIdentifiers), targets.getEntries().stream()
						.map(Target::getOrganizationIdentifierValue).collect(Collectors.joining(",")),
				task.getId());
	}

	private String getProjectIdentifier(TaskHelper helper, Task task)
	{
		return helper
				.getInputParameters(task, ConstantsDataSharing.CODESYSTEM_DATA_SHARING,
						ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_PROJECT_IDENTIFIER, Identifier.class)
				.map(i -> (Identifier) i.getValue())
				.filter(i -> ConstantsBase.NAMINGSYSTEM_MII_PROJECT_IDENTIFIER.equals(i.getSystem()))
				.map(Identifier::getValue).map(String::trim).findFirst().orElseThrow(() -> new RuntimeException(
						"No project-identifier present in task with id '" + task.getId() + "'"));
	}

	private String getContractUrl(TaskHelper helper, Task task)
	{
		return helper
				.getFirstInputParameterValue(task, ConstantsDataSharing.CODESYSTEM_DATA_SHARING,
						ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_CONTRACT_URL, UrlType.class)
				.map(UrlType::getValue).orElseThrow(
						() -> new RuntimeException("No contract-url present in task with id '" + task.getId() + "'"));
	}

	private List<String> getResearcherIdentifiers(TaskHelper helper, Task task)
	{
		return helper
				.getInputParameters(task, ConstantsDataSharing.CODESYSTEM_DATA_SHARING,
						ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_RESEARCHER_IDENTIFIER, Identifier.class)
				.map(i -> (Identifier) i.getValue())
				.filter(i -> ConstantsDataSharing.NAMINGSYSTEM_RESEARCHER_IDENTIFIER.equals(i.getSystem()))
				.map(Identifier::getValue).collect(Collectors.toList());
	}

	private List<Target> getTargets(TaskHelper helper, Task task, EndpointProvider endpointProvider,
			Variables variables)
	{
		return helper
				.getInputParametersWithExtension(task, ConstantsDataSharing.CODESYSTEM_DATA_SHARING,
						ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DIC_CORRELATION_KEY, StringType.class,
						ConstantsDataSharing.EXTENSION_URL_DIC_IDENTIFIER)
				.map(p -> transformDicCorrelationKeyInputToTarget(endpointProvider, p, variables)).toList();
	}

	private Target transformDicCorrelationKeyInputToTarget(EndpointProvider endpointProvider,
			Task.ParameterComponent input, Variables variables)
	{
		String organizationIdentifier = ((Reference) input
				.getExtensionByUrl(ConstantsDataSharing.EXTENSION_URL_DIC_IDENTIFIER).getValue()).getIdentifier()
				.getValue();
		String correlationKey = ((StringType) input.getValue()).asStringValue();

		return endpointProvider.getEndpoint(NamingSystems.OrganizationIdentifier.withValue(
				ConstantsBase.NAMINGSYSTEM_DSF_ORGANIZATION_IDENTIFIER_MEDICAL_INFORMATICS_INITIATIVE_CONSORTIUM),
				NamingSystems.OrganizationIdentifier.withValue(organizationIdentifier),
				CodeSystems.OrganizationRole.dic())
				.map(e -> variables.createTarget(organizationIdentifier, e.getIdentifierFirstRep().getValue(),
						e.getAddress(), correlationKey))
				.orElseThrow(() -> new RuntimeException("Could not find Endpoint of organization '"
						+ ConstantsBase.NAMINGSYSTEM_DSF_ORGANIZATION_IDENTIFIER_MEDICAL_INFORMATICS_INITIATIVE_CONSORTIUM
						+ "|" + organizationIdentifier + "'"));
	}
}
