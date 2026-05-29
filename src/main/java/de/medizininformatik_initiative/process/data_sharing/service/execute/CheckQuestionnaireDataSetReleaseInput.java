package de.medizininformatik_initiative.process.data_sharing.service.execute;

import java.util.Objects;
import java.util.stream.Stream;

import org.hl7.fhir.r4.model.PrimitiveType;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.ServiceTask;
import dev.dsf.bpe.v2.error.ErrorBoundaryEvent;
import dev.dsf.bpe.v2.variables.Variables;

public class CheckQuestionnaireDataSetReleaseInput implements ServiceTask
{
	private static final Logger logger = LoggerFactory.getLogger(CheckQuestionnaireDataSetReleaseInput.class);

	public CheckQuestionnaireDataSetReleaseInput()
	{
	}

	@Override
	public void execute(ProcessPluginApi api, Variables variables)
	{
		Task task = variables.getStartTask();
		String dmsIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DMS_IDENTIFIER);
		String projectIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);
		QuestionnaireResponse questionnaireResponse = variables.getLatestReceivedQuestionnaireResponse();

		if (projectIdentifierMatch(questionnaireResponse, projectIdentifier))
		{
			logger.info("Released data-set for transfer to DMS '{}' for project-identifier '{}' in Task '{}'",
					dmsIdentifier, projectIdentifier, api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task));
		}
		else
		{
			String expectedIdentifier = getProjectIdentifier(questionnaireResponse);
			logger.warn(
					"Could not release data-set to DMS '{}' for project-identifier '{}' for Task '{}': expected and provided project-identifier do not match (expected: {}, provided: {})",
					dmsIdentifier, projectIdentifier, api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task),
					expectedIdentifier, projectIdentifier.toLowerCase());

			String error = "Release data-set failed" + ConstantsBase.EXCEPTION_MESSAGE_DIVIDER
					+ "project-identifiers do not match (expected: " + projectIdentifier.toLowerCase() + ", provided:"
					+ expectedIdentifier + ")";
			throw new ErrorBoundaryEvent(ConstantsBase.CODESYSTEM_DATA_SET_STATUS_VALUE_NOT_SENT,
					error);
		}
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
