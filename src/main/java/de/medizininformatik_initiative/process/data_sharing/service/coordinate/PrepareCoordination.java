package de.medizininformatik_initiative.process.data_sharing.service.coordinate;

import java.util.List;
import java.util.stream.Collectors;

import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Task;
import org.hl7.fhir.r4.model.UrlType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.ServiceTask;
import dev.dsf.bpe.v2.service.TaskHelper;
import dev.dsf.bpe.v2.variables.Variables;

public class PrepareCoordination implements ServiceTask
{
	private static final Logger logger = LoggerFactory.getLogger(PrepareCoordination.class);

	public PrepareCoordination()
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

		String extractionPeriod = getExtractionPeriod(api.getTaskHelper(), task);
		variables.setString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_EXTRACTION_PERIOD, extractionPeriod);

		List<String> researcherIdentifiers = getResearcherIdentifiers(task);
		variables.setStringList(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_RESEARCHER_IDENTIFIERS,
				researcherIdentifiers);

		String dicIdentifiers = getDicIdentifiers(api.getTaskHelper(), task);
		variables.setBoolean(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_ALL_DATA_SETS_RECEIVED, false);

		String dmsIdentifier = getDmsIdentifier(api.getTaskHelper(), task);
		variables.setString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DMS_IDENTIFIER, dmsIdentifier);

		logger.info(
				"Starting coordination of approved data-sharing project for project-identifier '{}' with contract-url '{}', extraction-period '{}', researchers {}, DMS '{}' and DICs {} in Task '{}'",
				projectIdentifier, contractUrl, extractionPeriod, researcherIdentifiers, dmsIdentifier, dicIdentifiers,
				api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task));
	}

	private String getProjectIdentifier(TaskHelper helper, Task task)
	{
		return helper
				.getFirstInputParameterValue(task, ConstantsDataSharing.CODESYSTEM_DATA_SHARING,
						ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_PROJECT_IDENTIFIER, Identifier.class)
				.map(Identifier::getValue).map(String::trim)
				.orElseThrow(() -> new RuntimeException("Task.input:project-identifier missing"));
	}

	private String getContractUrl(TaskHelper helper, Task task)
	{
		return helper
				.getFirstInputParameterValue(task, ConstantsDataSharing.CODESYSTEM_DATA_SHARING,
						ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_CONTRACT_URL, UrlType.class)
				.map(UrlType::getValue).orElseThrow(() -> new RuntimeException("Task.input:contract-url missing"));
	}

	private String getExtractionPeriod(TaskHelper helper, Task task)
	{
		return helper
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

	private String getDicIdentifiers(TaskHelper helper, Task task)
	{
		return helper
				.getInputParameterValues(task, ConstantsDataSharing.CODESYSTEM_DATA_SHARING,
						ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DIC_IDENTIFIER, Reference.class)
				.filter(Reference::hasIdentifier).map(Reference::getIdentifier).map(Identifier::getValue)
				.collect(Collectors.joining(","));
	}

	private String getDmsIdentifier(TaskHelper helper, Task task)
	{
		return helper
				.getInputParameterValues(task, ConstantsDataSharing.CODESYSTEM_DATA_SHARING,
						ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DMS_IDENTIFIER, Reference.class)
				.filter(Reference::hasIdentifier).map(Reference::getIdentifier).map(Identifier::getValue).findFirst()
				.orElseThrow(() -> new RuntimeException("Task.input:dms-identifier missing"));
	}
}
