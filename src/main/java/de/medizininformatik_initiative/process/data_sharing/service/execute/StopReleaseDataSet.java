package de.medizininformatik_initiative.process.data_sharing.service.execute;

import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.ServiceTask;
import dev.dsf.bpe.v2.variables.Variables;

public class StopReleaseDataSet implements ServiceTask
{
	private static final Logger logger = LoggerFactory.getLogger(StopReleaseDataSet.class);

	public StopReleaseDataSet()
	{
	}

	@Override
	public void execute(ProcessPluginApi api, Variables variables)
	{
		logger.info(
				"Transfer-period for data-sharing data-set transfer to DMS '{}', project-identifier '{}' and contract-url '{} was closed for Task '{}'",
				variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DMS_IDENTIFIER),
				variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER),
				variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_CONTRACT_URL),
				api.getTaskHelper().getLocalVersionlessAbsoluteUrl(variables.getStartTask()));

		QuestionnaireResponse questionnaireResponse = variables.getFhirResource(
				ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_RELEASE_DATA_SET_INITIAL_QUESTIONNAIRE_RESPONSE);
		questionnaireResponse.setStatus(QuestionnaireResponse.QuestionnaireResponseStatus.STOPPED);
		api.getDsfClientProvider().getLocal().update(questionnaireResponse);
	}
}
