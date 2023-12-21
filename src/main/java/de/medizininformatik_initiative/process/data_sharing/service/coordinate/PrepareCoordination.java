package de.medizininformatik_initiative.process.data_sharing.service.coordinate;

import java.util.List;
import java.util.stream.Collectors;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
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
import dev.dsf.bpe.v1.variables.Variables;

public class PrepareCoordination extends AbstractServiceDelegate
{
	private static final Logger logger = LoggerFactory.getLogger(PrepareCoordination.class);

	public PrepareCoordination(ProcessPluginApi api)
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

		String dicIdentifiers = getDicIdentifiers(task);
		variables.setBoolean(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_ALL_DATA_SETS_RECEIVED, false);

		String dmsIdentifier = getDmsIdentifier(task);
		variables.setString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DMS_IDENTIFIER, dmsIdentifier);

		logger.info(
				"Starting coordination of approved data-sharing project [project-identifier: {} ; contract-url: {} ; extraction-period: {} ; researchers: {} ; dic: {} ; dms: {} ; task-id: {}]",
				projectIdentifier, contractUrl, extractionPeriod, String.join(",", researcherIdentifiers),
				dicIdentifiers, dmsIdentifier, task.getId());
	}

	private String getProjectIdentifier(Task task)
	{
		return task.getInput().stream().filter(i -> i.getType().getCoding().stream()
				.anyMatch(c -> ConstantsDataSharing.CODESYSTEM_DATA_SHARING.equals(c.getSystem())
						&& ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_PROJECT_IDENTIFIER.equals(c.getCode())))
				.filter(i -> i.getValue() instanceof Identifier).map(i -> (Identifier) i.getValue())
				.filter(i -> ConstantsBase.NAMINGSYSTEM_MII_PROJECT_IDENTIFIER.equals(i.getSystem()))
				.map(Identifier::getValue).findFirst().orElseThrow(() -> new RuntimeException(
						"No project-identifier present in Task with id '" + task.getId() + "'"));
	}

	private String getContractUrl(Task task)
	{
		return api.getTaskHelper()
				.getFirstInputParameterValue(task, ConstantsDataSharing.CODESYSTEM_DATA_SHARING,
						ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_CONTRACT_URL, UrlType.class)
				.map(UrlType::getValue).orElseThrow(
						() -> new RuntimeException("No contract-url present in Task with id '" + task.getId() + "'"));
	}

	private String getExtractionPeriod(Task task)
	{
		return api.getTaskHelper()
				.getFirstInputParameterStringValue(task, ConstantsDataSharing.CODESYSTEM_DATA_SHARING,
						ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_EXTRACTION_PERIOD)
				.orElse(ConstantsDataSharing.DATA_EXTRACTION_PERIOD_DEFAULT_VALUE);
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

	private String getDicIdentifiers(Task task)
	{
		return api.getTaskHelper()
				.getInputParameterValues(task, ConstantsDataSharing.CODESYSTEM_DATA_SHARING,
						ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DIC_IDENTIFIER, Reference.class)
				.filter(Reference::hasIdentifier).map(Reference::getIdentifier).map(Identifier::getValue)
				.collect(Collectors.joining(","));
	}

	private String getDmsIdentifier(Task task)
	{
		return api.getTaskHelper()
				.getInputParameterValues(task, ConstantsDataSharing.CODESYSTEM_DATA_SHARING,
						ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DMS_IDENTIFIER, Reference.class)
				.filter(Reference::hasIdentifier).map(Reference::getIdentifier).map(Identifier::getValue).findFirst()
				.orElseThrow(
						() -> new RuntimeException("No DMS identifier present in Task with id '" + task.getId() + "'"));
	}
}
