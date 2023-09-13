package de.medizininformatik_initiative.process.data_sharing.service.merge;

import java.util.Optional;
import java.util.stream.Stream;

import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Task;
import org.hl7.fhir.r4.model.UriType;
import org.hl7.fhir.r4.model.UrlType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.variables.Variables;

public class CheckQuestionnaireMergedDataSetReleaseInput extends AbstractServiceDelegate
{
	private static final Logger logger = LoggerFactory.getLogger(CheckQuestionnaireMergedDataSetReleaseInput.class);

	public CheckQuestionnaireMergedDataSetReleaseInput(ProcessPluginApi api)
	{
		super(api);
	}

	@Override
	protected void doExecute(DelegateExecution execution, Variables variables)
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

			logger.info(
					"Released merged data-set for HRP and data-sharing project '{}' referenced in Task with id '{}'",
					projectIdentifier, task.getId());
		}
		else
		{
			String message = "Could not release merged data-set for HRP and data-sharing project '" + projectIdentifier
					+ "' referenced in Task with id '" + task.getId()
					+ "': expected and provided project identifier do not match (" + projectIdentifier.toLowerCase()
					+ "/" + getProvidedProjectIdentifierAsLowerCase(questionnaireResponse)
					+ ") or QuestionnaireResponse with id '"
					+ getDsfFhirServerAbsoluteId(questionnaireResponse.getIdElement())
					+ "' is missing item with linkId '"
					+ ConstantsDataSharing.QUESTIONNAIRES_RELEASE_DATA_SET_ITEM_DATA_SET_URL + "'";

			variables.setString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_MERGE_RELEASE_ERROR_MESSAGE,
					message);

			throw new BpmnError(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_MERGE_RELEASE_ERROR, message);
		}
	}

	private Optional<String> getDataSetUrl(QuestionnaireResponse questionnaireResponse)
	{
		return questionnaireResponse.getItem().stream()
				.filter(i -> ConstantsDataSharing.QUESTIONNAIRES_RELEASE_DATA_SET_ITEM_DATA_SET_URL
						.equals(i.getLinkId()))
				.filter(QuestionnaireResponse.QuestionnaireResponseItemComponent::hasAnswer)
				.flatMap(i -> i.getAnswer().stream())
				.filter(QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent::hasValue)
				.map(QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent::getValue)
				.filter(a -> UriType.class.isAssignableFrom(a.getClass())).map(a -> (UriType) a)
				.filter(PrimitiveType::hasValue).map(PrimitiveType::getValue).findFirst();
	}

	private void storeDataSetUrlAsTaskOutput(Task leadingTask, String dataSetUrl)
	{
		Optional<Task.TaskOutputComponent> output = leadingTask.getOutput().stream()
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
			leadingTask.addOutput(dataSetUrlOutput);
		});
	}

	private boolean projectIdentifierMatch(QuestionnaireResponse questionnaireResponse,
			String expectedProjectIdentifier)
	{
		return getProjectIdentifiersAsLowerCase(questionnaireResponse).anyMatch(
				foundProjectIdentifier -> expectedProjectIdentifier.toLowerCase().equals(foundProjectIdentifier));
	}

	private String getProvidedProjectIdentifierAsLowerCase(QuestionnaireResponse questionnaireResponse)
	{
		return getProjectIdentifiersAsLowerCase(questionnaireResponse).findFirst().orElse("unknown");
	}

	private Stream<String> getProjectIdentifiersAsLowerCase(QuestionnaireResponse questionnaireResponse)
	{
		return questionnaireResponse.getItem().stream()
				.filter(i -> ConstantsDataSharing.QUESTIONNAIRES_RELEASE_DATA_SET_ITEM_RELEASE.equals(i.getLinkId()))
				.flatMap(i -> i.getAnswer().stream()).filter(a -> a.getValue() instanceof StringType)
				.map(a -> (StringType) a.getValue()).map(PrimitiveType::getValue).map(String::toLowerCase)
				.map(String::trim);
	}

	private String getDsfFhirServerAbsoluteId(IdType idType)
	{
		return new IdType(api.getFhirWebserviceClientProvider().getLocalWebserviceClient().getBaseUrl(),
				idType.getResourceType(), idType.getIdPart(), null).getValue();
	}
}
