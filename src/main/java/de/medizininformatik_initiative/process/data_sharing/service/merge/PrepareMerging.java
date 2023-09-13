package de.medizininformatik_initiative.process.data_sharing.service.merge;

import java.util.List;
import java.util.stream.Collectors;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Task;
import org.hl7.fhir.r4.model.UrlType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.process.data_sharing.variables.Researchers;
import de.medizininformatik_initiative.process.data_sharing.variables.ResearchersValues;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.constants.NamingSystems;
import dev.dsf.bpe.v1.variables.Target;
import dev.dsf.bpe.v1.variables.Targets;
import dev.dsf.bpe.v1.variables.Variables;

public class PrepareMerging extends AbstractServiceDelegate
{
	private static final Logger logger = LoggerFactory.getLogger(PrepareMerging.class);

	public PrepareMerging(ProcessPluginApi api)
	{
		super(api);
	}

	@Override
	protected void doExecute(DelegateExecution execution, Variables variables)
	{
		Task task = variables.getStartTask();

		String projectIdentifier = getProjectIdentifier(task);
		variables.setString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER, projectIdentifier);

		String contractUrl = getContractUrl(task);
		variables.setString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_CONTRACT_URL, contractUrl);

		String extractionPeriod = getExtractionPeriod(task);
		variables.setString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_EXTRACTION_PERIOD, extractionPeriod);

		List<String> researcherIdentifiers = getResearcherIdentifiers(task);
		variables.setVariable(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_RESEARCHER_IDENTIFIERS,
				ResearchersValues.create(new Researchers(researcherIdentifiers)));

		List<Target> targetsList = getTargets(task, variables);
		Targets targets = variables.createTargets(targetsList);
		variables.setTargets(targets);

		logger.info(
				"Starting data-set reception and merging of approved data sharing project [project-identifier: {} ; contract-url: {} ; extraction-period: {} ; researchers: {} ; dic: {} ; task-id: {}]",
				projectIdentifier, contractUrl, extractionPeriod, String.join(",", researcherIdentifiers),
				targets.getEntries().stream().map(Target::getOrganizationIdentifierValue)
						.collect(Collectors.joining(",")),
				task.getId());
	}

	private String getProjectIdentifier(Task task)
	{
		return task.getInput().stream().filter(i -> i.getType().getCoding().stream()
				.anyMatch(c -> ConstantsDataSharing.CODESYSTEM_DATA_SHARING.equals(c.getSystem())
						&& ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_PROJECT_IDENTIFIER.equals(c.getCode())))
				.filter(i -> i.getValue() instanceof Identifier).map(i -> (Identifier) i.getValue())
				.filter(i -> ConstantsBase.NAMINGSYSTEM_MII_PROJECT_IDENTIFIER.equals(i.getSystem()))
				.map(Identifier::getValue).findFirst().orElseThrow(() -> new RuntimeException(
						"No project-identifier present in task with id '" + task.getId() + "'"));
	}

	private String getContractUrl(Task task)
	{
		return api.getTaskHelper()
				.getFirstInputParameterValue(task, ConstantsDataSharing.CODESYSTEM_DATA_SHARING,
						ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_CONTRACT_URL, UrlType.class)
				.map(UrlType::getValue).orElseThrow(
						() -> new RuntimeException("No contract-url present in task with id '" + task.getId() + "'"));
	}

	private String getExtractionPeriod(Task task)
	{
		return api.getTaskHelper()
				.getFirstInputParameterStringValue(task, ConstantsDataSharing.CODESYSTEM_DATA_SHARING,
						ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_EXTRACTION_PERIOD)
				.orElseThrow(() -> new RuntimeException("No extraction period provided by HRP"));
	}

	private List<String> getResearcherIdentifiers(Task task)
	{
		return task.getInput().stream()
				.filter(i -> i.getType().getCoding().stream()
						.anyMatch(c -> ConstantsDataSharing.CODESYSTEM_DATA_SHARING.equals(c.getSystem())
								&& ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_RESEARCHER_IDENTIFIER
										.equals(c.getCode())))
				.filter(i -> i.getValue() instanceof Identifier).map(i -> (Identifier) i.getValue())
				.filter(i -> ConstantsDataSharing.NAMINGSYSTEM_RESEARCHER_IDENTIFIER.equals(i.getSystem()))
				.map(Identifier::getValue).collect(Collectors.toList());
	}

	private List<Target> getTargets(Task task, Variables variables)
	{
		return api.getTaskHelper()
				.getInputParametersWithExtension(task, ConstantsDataSharing.CODESYSTEM_DATA_SHARING,
						ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DIC_CORRELATION_KEY, StringType.class,
						ConstantsDataSharing.EXTENSION_URL_DIC_IDENTIFIER)
				.map(p -> transformDicCorrelationKeyInputToTarget(p, variables)).toList();
	}

	private Target transformDicCorrelationKeyInputToTarget(Task.ParameterComponent input, Variables variables)
	{
		String organizationIdentifier = ((Reference) input
				.getExtensionByUrl(ConstantsDataSharing.EXTENSION_URL_DIC_IDENTIFIER).getValue()).getIdentifier()
				.getValue();
		String correlationKey = ((StringType) input.getValue()).asStringValue();

		return api.getEndpointProvider().getEndpoint(NamingSystems.OrganizationIdentifier.withValue(
				ConstantsBase.NAMINGSYSTEM_DSF_ORGANIZATION_IDENTIFIER_MEDICAL_INFORMATICS_INITIATIVE_CONSORTIUM),
				NamingSystems.OrganizationIdentifier.withValue(organizationIdentifier),
				new Coding().setSystem(ConstantsBase.CODESYSTEM_DSF_ORGANIZATION_ROLE)
						.setCode(ConstantsBase.CODESYSTEM_DSF_ORGANIZATION_ROLE_VALUE_DIC))
				.map(e -> variables.createTarget(organizationIdentifier, e.getIdentifierFirstRep().getValue(),
						e.getAddress(), correlationKey))
				.orElseThrow(() -> new RuntimeException(
						"No endpoint of found for organization '" + organizationIdentifier + "'"));
	}
}
