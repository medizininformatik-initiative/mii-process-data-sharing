package de.medizininformatik_initiative.process.data_sharing.service.execute;

import java.util.stream.Stream;

import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.variables.Variables;

public class CheckQuestionnaireDataSetReleaseInput extends AbstractServiceDelegate
{
	private static final Logger logger = LoggerFactory.getLogger(CheckQuestionnaireDataSetReleaseInput.class);

	public CheckQuestionnaireDataSetReleaseInput(ProcessPluginApi api)
	{
		super(api);
	}

	@Override
	protected void doExecute(DelegateExecution execution, Variables variables)
	{
		Task task = variables.getStartTask();
		String dmsIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DMS_IDENTIFIER);
		String projectIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);
		QuestionnaireResponse questionnaireResponse = variables.getLatestReceivedQuestionnaireResponse();

		if (projectIdentifierMatch(questionnaireResponse, projectIdentifier))
		{
			logger.info(
					"Released data-set provided for DMS '{}' and data-sharing project '{}' referenced in Task with id '{}'",
					dmsIdentifier, projectIdentifier, task.getId());
		}
		else
		{
			String message = "Could not release data-set for DMS '" + dmsIdentifier + "' and data-sharing project '"
					+ projectIdentifier + "' belonging to Task with id '" + task.getId()
					+ "': expected and provided project identifier do not match (" + projectIdentifier.toLowerCase()
					+ "/" + getProvidedProjectIdentifierAsLowerCase(questionnaireResponse) + ")";

			variables.setString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_EXECUTE_ERROR_MESSAGE,
					message);

			throw new BpmnError(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_EXECUTE_ERROR, message);
		}
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
				.filter(i -> ConstantsDataSharing.QUESTIONNAIRES_ITEM_RELEASE.equals(i.getLinkId()))
				.flatMap(i -> i.getAnswer().stream()).filter(a -> a.getValue() instanceof StringType)
				.map(a -> (StringType) a.getValue()).map(PrimitiveType::getValue).map(String::toLowerCase)
				.map(String::trim);
	}
}
