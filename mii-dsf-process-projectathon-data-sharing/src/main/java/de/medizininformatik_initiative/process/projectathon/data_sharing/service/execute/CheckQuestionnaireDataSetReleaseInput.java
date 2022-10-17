package de.medizininformatik_initiative.process.projectathon.data_sharing.service.execute;

import java.util.stream.Stream;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.highmed.dsf.bpe.ConstantsBase;
import org.highmed.dsf.bpe.delegate.AbstractServiceDelegate;
import org.highmed.dsf.fhir.authorization.read.ReadAccessHelper;
import org.highmed.dsf.fhir.client.FhirWebserviceClientProvider;
import org.highmed.dsf.fhir.task.TaskHelper;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.StringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.medizininformatik_initiative.process.projectathon.data_sharing.ConstantsDataSharing;

public class CheckQuestionnaireDataSetReleaseInput extends AbstractServiceDelegate
{
	private static final Logger logger = LoggerFactory.getLogger(CheckQuestionnaireDataSetReleaseInput.class);

	public CheckQuestionnaireDataSetReleaseInput(FhirWebserviceClientProvider clientProvider, TaskHelper taskHelper,
			ReadAccessHelper readAccessHelper)
	{
		super(clientProvider, taskHelper, readAccessHelper);
	}

	@Override
	protected void doExecute(DelegateExecution execution)
	{
		String cosIdentifier = (String) execution
				.getVariable(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_COS_IDENTIFIER);
		String projectIdentifier = (String) execution
				.getVariable(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);
		QuestionnaireResponse questionnaireResponse = (QuestionnaireResponse) execution
				.getVariable(ConstantsBase.BPMN_EXECUTION_VARIABLE_QUESTIONNAIRE_RESPONSE_COMPLETED);

		if (projectIdentifierMatch(questionnaireResponse, projectIdentifier))
		{
			logger.info(
					"Released data-set provided for COS-identifier='{}' and project-identifier='{}' referenced in Task with id='{}'",
					cosIdentifier, projectIdentifier, getLeadingTaskFromExecutionVariables(execution).getId());
			execution.setVariable(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SET_RELEASED, true);
		}
		else
		{
			logger.warn(
					"Could not release data-set for COS-identifier='{}' and project-identifier='{}' referenced in Task with id='{}': expected and provided project identifier do not match ({}/{}), restarting user task",
					cosIdentifier, projectIdentifier, getLeadingTaskFromExecutionVariables(execution).getId(),
					projectIdentifier.toLowerCase(), getProvidedProjectIdentifierAsLowerCase(questionnaireResponse));
			execution.setVariable(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SET_RELEASED, false);
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
				.filter(i -> ConstantsDataSharing.QUESTIONNAIRES_RELEASE_DATA_SET_ITEM_RELEASE.equals(i.getLinkId()))
				.flatMap(i -> i.getAnswer().stream()).filter(a -> a.getValue() instanceof StringType)
				.map(a -> (StringType) a.getValue()).map(PrimitiveType::getValue).map(String::toLowerCase)
				.map(String::trim);
	}
}