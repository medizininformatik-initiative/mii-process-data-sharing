package de.medizininformatik_initiative.process.data_sharing.service.merge;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.hl7.fhir.r4.model.PrimitiveType;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Task;
import org.hl7.fhir.r4.model.UriType;
import org.hl7.fhir.r4.model.UrlType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.ServiceTask;
import dev.dsf.bpe.v2.error.ErrorBoundaryEvent;
import dev.dsf.bpe.v2.variables.Variables;

public class CheckQuestionnaireMergedDataSetReleaseInput implements ServiceTask
{
	private static final Logger logger = LoggerFactory.getLogger(CheckQuestionnaireMergedDataSetReleaseInput.class);

	public CheckQuestionnaireMergedDataSetReleaseInput()
	{
	}

	@Override
	public void execute(ProcessPluginApi api, Variables variables)
	{
		Task task = variables.getStartTask();
		String projectIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);
		QuestionnaireResponse questionnaireResponse = variables.getLatestReceivedQuestionnaireResponse();

		Optional<String> dataSetUrlOptional = getDataSetUrl(questionnaireResponse);

		if (projectIdentifierMatch(questionnaireResponse, projectIdentifier) && dataSetUrlOptional.isPresent())
		{
			String dataSetUrl = dataSetUrlOptional.get();
			storeDataSetUrlAsTaskOutput(task, dataSetUrl);
			variables.updateTask(task);
			variables.setString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SET_URL, dataSetUrl);

			logger.info("Released merged data-set for HRP and data-sharing project '{}' in Task '{}'",
					projectIdentifier, task.getId());
		}
		else
		{
			String expectedIdentifier = getProjectIdentifier(questionnaireResponse);
			logger.warn(
					"Could not release merged data-set for HRP and data-sharing project '{}' referenced in Task with id '{}': expected and provided project identifier do not match (expected: {}, provided: {}) or merged data-set URL is not present",
					projectIdentifier, task.getId(), expectedIdentifier, projectIdentifier.toLowerCase());

			String error = "Release merged data-set failed" + ConstantsBase.EXCEPTION_MESSAGE_DIVIDER
					+ " project identifier do not match (expected: " + projectIdentifier.toLowerCase() + ", provided:"
					+ expectedIdentifier + ") or merged data-set URL not present";
			throw new ErrorBoundaryEvent(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_MERGE_RELEASE_ERROR,
					error);
		}
	}

	private Optional<String> getDataSetUrl(QuestionnaireResponse questionnaireResponse)
	{
		return questionnaireResponse.getItem().stream()
				.filter(i -> ConstantsDataSharing.QUESTIONNAIRES_ITEM_DATA_SET_URL.equals(i.getLinkId()))
				.filter(QuestionnaireResponse.QuestionnaireResponseItemComponent::hasAnswer)
				.flatMap(i -> i.getAnswer().stream())
				.filter(QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent::hasValue)
				.map(QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent::getValue)
				.filter(a -> UriType.class.isAssignableFrom(a.getClass())).map(a -> (UriType) a)
				.filter(PrimitiveType::hasValue).map(PrimitiveType::getValue).findFirst();
	}

	private void storeDataSetUrlAsTaskOutput(Task startTask, String dataSetUrl)
	{
		Optional<Task.TaskOutputComponent> output = startTask.getOutput().stream()
				.filter(Task.TaskOutputComponent::hasType)
				.filter(o -> o.getType().getCoding().stream().anyMatch(
						c -> ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DATA_SET_URL.equals(c.getCode())
								&& ConstantsDataSharing.CODESYSTEM_DATA_SHARING.equals(c.getSystem())))
				.findFirst();

		output.ifPresentOrElse(o -> o.setValue(new UrlType().setValue(dataSetUrl)), () ->
		{
			Task.TaskOutputComponent dataSetUrlOutput = new Task.TaskOutputComponent();
			dataSetUrlOutput.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
					.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DATA_SET_URL);
			dataSetUrlOutput.setValue(new UrlType().setValue(dataSetUrl));
			startTask.addOutput(dataSetUrlOutput);
		});
	}

	private boolean projectIdentifierMatch(QuestionnaireResponse questionnaireResponse,
			String expectedProjectIdentifier)
	{
		return getProjectIdentifiers(questionnaireResponse).anyMatch(foundProjectIdentifier -> expectedProjectIdentifier
				.trim().equalsIgnoreCase(foundProjectIdentifier.trim()));
	}

	private String getProjectIdentifier(QuestionnaireResponse questionnaireResponse)
	{
		return getProjectIdentifiers(questionnaireResponse).findFirst().orElse("unknown");
	}

	private Stream<String> getProjectIdentifiers(QuestionnaireResponse questionnaireResponse)
	{
		return questionnaireResponse.getItem().stream()
				.filter(i -> ConstantsDataSharing.QUESTIONNAIRES_ITEM_RELEASE.equals(i.getLinkId()))
				.flatMap(i -> i.getAnswer().stream()).filter(a -> a.getValue() instanceof StringType)
				.map(a -> (StringType) a.getValue()).map(PrimitiveType::getValue).filter(Objects::nonNull);
	}
}
