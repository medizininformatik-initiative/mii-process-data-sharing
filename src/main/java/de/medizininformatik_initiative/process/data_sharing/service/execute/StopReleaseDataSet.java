package de.medizininformatik_initiative.process.data_sharing.service.execute;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.variables.Variables;

public class StopReleaseDataSet extends AbstractServiceDelegate
{
	private static final Logger logger = LoggerFactory.getLogger(StopReleaseDataSet.class);

	public StopReleaseDataSet(ProcessPluginApi api)
	{
		super(api);
	}

	@Override
	protected void doExecute(DelegateExecution execution, Variables variables)
	{
		logger.info(
				"Extraction and transfer period of approved data sharing project was closed [project-identifier: {} ; dms: {} ; contract-url: {} ; task-id: {}]",
				variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER),
				variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DMS_IDENTIFIER),
				variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_CONTRACT_URL),
				variables.getStartTask().getId());

		QuestionnaireResponse questionnaireResponse = variables.getResource(
				ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_RELEASE_DATA_SET_INITIAL_QUESTIONNAIRE_RESPONSE);
		questionnaireResponse.setStatus(QuestionnaireResponse.QuestionnaireResponseStatus.STOPPED);
		api.getFhirWebserviceClientProvider().getLocalWebserviceClient().update(questionnaireResponse);
	}
}
