package de.medizininformatik_initiative.process.data_sharing.service.coordinate;

import java.util.regex.Pattern;

import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.ServiceTask;
import dev.dsf.bpe.v2.service.QuestionnaireResponseHelper;
import dev.dsf.bpe.v2.variables.Variables;

public class CheckQuestionnaireConsolidateDataSetsReleaseInput implements ServiceTask
{
	private static final Logger logger = LoggerFactory
			.getLogger(CheckQuestionnaireConsolidateDataSetsReleaseInput.class);

	private static final Pattern PERIOD_ISO_8601 = Pattern.compile(
			"P(?:([0-9]+)Y)?(?:([0-9]+)M)?(?:([0-9]+)D)?(T(?:([0-9]+)H)?(?:([0-9]+)M)?(?:([0-9]+)(?:[.,]([0-9]{0,9}))?S)?)?");

	public CheckQuestionnaireConsolidateDataSetsReleaseInput()
	{
	}

	@Override
	public void execute(ProcessPluginApi api, Variables variables)
	{
		Task task = variables.getStartTask();
		QuestionnaireResponse questionnaireResponse = variables.getLatestReceivedQuestionnaireResponse();

		boolean isReleased = isReleased(api.getQuestionnaireResponseHelper(), questionnaireResponse);
		variables.setBoolean(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_CONSOLIDATE_DATA_SETS_RELEASED, isReleased);

		if (!isReleased)
		{
			String extractionPeriod = getExtendedExtractionPeriod(api.getQuestionnaireResponseHelper(),
					questionnaireResponse);
			variables.setString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_EXTRACTION_PERIOD, extractionPeriod);

			String projectIdentifier = variables
					.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);
			String dmsIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DMS_IDENTIFIER);

			logger.info(
					"Extending data extraction period for the project-identifier '{}' and DMS '{}' for '{}' in Task '{}'",
					projectIdentifier, dmsIdentifier, extractionPeriod,
					api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task));
		}
	}

	private boolean isReleased(QuestionnaireResponseHelper helper, QuestionnaireResponse questionnaireResponse)
	{
		return helper
				.getFirstItemLeaveMatchingLinkId(questionnaireResponse,
						ConstantsDataSharing.QUESTIONNAIRES_ITEM_RELEASE)
				.filter(QuestionnaireResponse.QuestionnaireResponseItemComponent::hasAnswer)
				.map(QuestionnaireResponse.QuestionnaireResponseItemComponent::getAnswerFirstRep)
				.filter(QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent::hasValue)
				.map(QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent::getValue)
				.filter(a -> a instanceof BooleanType).map(b -> ((BooleanType) b).getValue()).orElse(false);
	}

	private String getExtendedExtractionPeriod(QuestionnaireResponseHelper helper,
			QuestionnaireResponse questionnaireResponse)
	{
		return helper
				.getFirstItemLeaveMatchingLinkId(questionnaireResponse,
						ConstantsDataSharing.QUESTIONNAIRES_ITEM_EXTENDED_EXTRACTION_PERIOD)
				.filter(QuestionnaireResponse.QuestionnaireResponseItemComponent::hasAnswer)
				.map(QuestionnaireResponse.QuestionnaireResponseItemComponent::getAnswerFirstRep)
				.filter(QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent::hasValue)
				.map(QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent::getValue)
				.filter(a -> a instanceof StringType).map(b -> ((StringType) b).getValue()).filter(this::matchesIso8601)
				.orElse(ConstantsDataSharing.EXTENDED_DATA_EXTRACTION_PERIOD_DEFAULT_VALUE);
	}

	private boolean matchesIso8601(String period)
	{
		boolean matches = PERIOD_ISO_8601.matcher(period).matches();

		if (!matches)
			logger.warn(
					"Entered extended extraction period '{}' not matching ISO 8601 format, using default extension period '{}'",
					period, ConstantsDataSharing.EXTENDED_DATA_EXTRACTION_PERIOD_DEFAULT_VALUE);

		return matches;
	}
}
