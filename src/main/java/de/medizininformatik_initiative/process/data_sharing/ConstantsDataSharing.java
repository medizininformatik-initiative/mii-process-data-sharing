package de.medizininformatik_initiative.process.data_sharing;

import de.medizininformatik_initiative.processes.common.util.ConstantsBase;

public interface ConstantsDataSharing
{
	String PROCESS_NAME_COORDINATE_DATA_SHARING = "coordinateDataSharing";
	String PROCESS_NAME_EXECUTE_DATA_SHARING = "executeDataSharing";
	String PROCESS_NAME_MERGE_DATA_SHARING = "mergeDataSharing";

	String PROCESS_NAME_FULL_COORDINATE_DATA_SHARING = ConstantsBase.PROCESS_MII_NAME_BASE
			+ PROCESS_NAME_COORDINATE_DATA_SHARING;
	String PROCESS_NAME_FULL_EXECUTE_DATA_SHARING = ConstantsBase.PROCESS_MII_NAME_BASE
			+ PROCESS_NAME_EXECUTE_DATA_SHARING;

	String PROCESS_NAME_FULL_MERGE_DATA_SHARING = ConstantsBase.PROCESS_MII_NAME_BASE + PROCESS_NAME_MERGE_DATA_SHARING;

	String PROFILE_TASK_CONSOLIDATE_DATA_SETS = "http://medizininformatik-initiative.de/fhir/StructureDefinition/task-consolidate-data-sets";
	String PROFILE_TASK_CONSOLIDATE_DATA_SETS_PROCESS_URI = ConstantsBase.PROCESS_MII_URI_BASE
			+ PROCESS_NAME_MERGE_DATA_SHARING;
	String PROFILE_TASK_CONSOLIDATE_DATA_SETS_MESSAGE_NAME = "consolidateDataSets";

	String PROFILE_TASK_COORDINATE_DATA_SHARING = "http://medizininformatik-initiative.de/fhir/StructureDefinition/task-coordinate-data-sharing";
	String PROFILE_TASK_COORDINATE_DATA_SHARING_PROCESS_URI = ConstantsBase.PROCESS_MII_URI_BASE
			+ PROCESS_NAME_COORDINATE_DATA_SHARING;
	String PROFILE_TASK_COORDINATE_DATA_SHARING_MESSAGE_NAME = "coordinateDataSharing";

	String PROFILE_TASK_EXECUTE_DATA_SHARING = "http://medizininformatik-initiative.de/fhir/StructureDefinition/task-execute-data-sharing";
	String PROFILE_TASK_EXECUTE_DATA_SHARING_PROCESS_URI = ConstantsBase.PROCESS_MII_URI_BASE
			+ PROCESS_NAME_EXECUTE_DATA_SHARING;
	String PROFILE_TASK_EXECUTE_DATA_SHARING_MESSAGE_NAME = "executeDataSharing";

	String PROFILE_TASK_STOP_EXECUTE_DATA_SHARING = "http://medizininformatik-initiative.de/fhir/StructureDefinition/task-stop-execute-data-sharing";
	String PROFILE_TASK_STOP_EXECUTE_DATA_SHARING_PROCESS_URI = ConstantsBase.PROCESS_MII_URI_BASE
			+ PROCESS_NAME_EXECUTE_DATA_SHARING;
	String PROFILE_TASK_STOP_EXECUTE_DATA_SHARING_MESSAGE_NAME = "stopExecuteDataSharing";

	String PROFILE_TASK_MERGE_DATA_SHARING = "http://medizininformatik-initiative.de/fhir/StructureDefinition/task-merge-data-sharing";
	String PROFILE_TASK_MERGE_DATA_SHARING_PROCESS_URI = ConstantsBase.PROCESS_MII_URI_BASE
			+ PROCESS_NAME_MERGE_DATA_SHARING;
	String PROFILE_TASK_MERGE_DATA_SHARING_MESSAGE_NAME = "mergeDataSharing";

	String PROFILE_TASK_SEND_DATA_SET = "http://medizininformatik-initiative.de/fhir/StructureDefinition/task-send-data-set";
	String PROFILE_TASK_SEND_DATA_SET_PROCESS_URI = ConstantsBase.PROCESS_MII_URI_BASE
			+ PROCESS_NAME_MERGE_DATA_SHARING;
	String PROFILE_TASK_SEND_DATA_SET_MESSAGE_NAME = "sendDataSet";

	String PROFILE_TASK_STATUS_DATA_SET = "http://medizininformatik-initiative.de/fhir/StructureDefinition/task-status-data-set";
	String PROFILE_TASK_STATUS_DATA_SET_PROCESS_URI = ConstantsBase.PROCESS_MII_URI_BASE
			+ PROCESS_NAME_EXECUTE_DATA_SHARING;
	String PROFILE_TASK_STATUS_DATA_SET_MESSAGE_NAME = "statusDataSet";

	String PROFILE_TASK_RECEIVED_DATA_SET = "http://medizininformatik-initiative.de/fhir/StructureDefinition/task-received-data-set";
	String PROFILE_TASK_RECEIVED_DATA_SET_PROCESS_URI = ConstantsBase.PROCESS_MII_URI_BASE
			+ PROCESS_NAME_COORDINATE_DATA_SHARING;
	String PROFILE_TASK_RECEIVED_DATA_SET_MESSAGE_NAME = "receivedDataSet";

	String PROFILE_TASK_MERGED_DATA_SET = "http://medizininformatik-initiative.de/fhir/StructureDefinition/task-merged-data-set";
	String PROFILE_TASK_MERGED_DATA_SET_PROCESS_URI = ConstantsBase.PROCESS_MII_URI_BASE
			+ PROCESS_NAME_COORDINATE_DATA_SHARING;
	String PROFILE_TASK_MERGED_DATA_SET_MESSAGE_NAME = "mergedDataSet";

	String EXTENSION_URL_DIC_IDENTIFIER = "http://medizininformatik-initiative.de/fhir/Extension/dic-identifier";

	String BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER = "projectIdentifier";
	String BPMN_EXECUTION_VARIABLE_CONTRACT_URL = "contractUrl";
	String BPMN_EXECUTION_VARIABLE_EXTRACTION_PERIOD = "extractionPeriod";
	String BPMN_EXECUTION_VARIABLE_RESEARCHER_IDENTIFIERS = "researcherIdentifiers";
	String BPMN_EXECUTION_VARIABLE_DMS_IDENTIFIER = "dmsIdentifier";
	String BPMN_EXECUTION_VARIABLE_DOCUMENT_REFERENCE = "documentReference";
	String BPMN_EXECUTION_VARIABLE_DATA_RESOURCE = "dataResource";
	String BPMN_EXECUTION_VARIABLE_RELEASE_DATA_SET_INITIAL_QUESTIONNAIRE_RESPONSE = "releaseDataSetInitialQuestionnaireResponse";
	String BPMN_EXECUTION_VARIABLE_DATA_SET = "dataSet";
	String BPMN_EXECUTION_VARIABLE_DATA_SET_ENCRYPTED = "dataSetEncrypted";
	String BPMN_EXECUTION_VARIABLE_DATA_SET_REFERENCE = "dataSetReference";
	String BPMN_EXECUTION_VARIABLE_DATA_SET_URL = "dataSetUrl";
	String BPMN_EXECUTION_VARIABLE_CONSOLIDATE_DATA_SETS_RELEASED = "consolidateDataSetReleased";
	String BPMN_EXECUTION_VARIABLE_ALL_DATA_SETS_RECEIVED = "allDataSetsReceived";

	String BPMN_EXECUTION_VARIABLE_DATA_SHARING_COORDINATE_ERROR = "coordinateDataSharingError";
	String BPMN_EXECUTION_VARIABLE_DATA_SHARING_EXECUTE_ERROR = "executeDataSharingError";
	String BPMN_EXECUTION_VARIABLE_DATA_SHARING_EXECUTE_ERROR_MESSAGE = "executeDataSharingErrorMessage";
	String BPMN_EXECUTION_VARIABLE_DATA_SHARING_MERGE_RECEIVE_ERROR = "mergeReceiveDataSharingError";

	String BPMN_EXECUTION_VARIABLE_DATA_SHARING_MERGE_RECEIVE_ERROR_EXISTS = "mergeReceiveDataSharingErrorExists";
	String BPMN_EXECUTION_VARIABLE_DATA_SHARING_MERGE_RECEIVE_ERROR_MESSAGE = "mergeReceiveDataSharingErrorMessage";
	String BPMN_EXECUTION_VARIABLE_DATA_SHARING_MERGE_RELEASE_ERROR = "mergeReleaseDataSharingError";
	String BPMN_EXECUTION_VARIABLE_DATA_SHARING_MERGE_RELEASE_ERROR_MESSAGE = "mergeReleaseDataSharingErrorMessage";

	String NAMINGSYSTEM_RESEARCHER_IDENTIFIER = "http://medizininformatik-initiative.de/sid/researcher-identifier";

	String CODESYSTEM_DATA_SHARING = "http://medizininformatik-initiative.de/fhir/CodeSystem/data-sharing";
	String CODESYSTEM_DATA_SHARING_VALUE_RESEARCHER_IDENTIFIER = "researcher-identifier";
	String CODESYSTEM_DATA_SHARING_VALUE_DIC_IDENTIFIER = "dic-identifier";
	String CODESYSTEM_DATA_SHARING_VALUE_DIC_CORRELATION_KEY = "dic-correlation-key";
	String CODESYSTEM_DATA_SHARING_VALUE_DMS_IDENTIFIER = "dms-identifier";
	String CODESYSTEM_DATA_SHARING_VALUE_PROJECT_IDENTIFIER = "project-identifier";
	String CODESYSTEM_DATA_SHARING_VALUE_CONTRACT_URL = "contract-url";
	String CODESYSTEM_DATA_SHARING_VALUE_EXTRACTION_PERIOD = "extraction-period";
	String CODESYSTEM_DATA_SHARING_VALUE_DATA_SET_URL = "data-set-url";
	String CODESYSTEM_DATA_SHARING_VALUE_DATA_SET_REFERENCE = "data-set-reference";
	String CODESYSTEM_DATA_SHARING_VALUE_DATA_SET_MISSING = "data-set-missing";
	String CODESYSTEM_DATA_SHARING_VALUE_DOCUMENT_REFERENCE_REFERENCE = "document-reference-reference";

	String CODESYSTEM_DATA_SHARING_VALUE_DATA_SET_STATUS = "data-set-status";

	String QUESTIONNAIRES_ITEM_DISPLAY = "display";
	String QUESTIONNAIRES_ITEM_RELEASE = "release";
	String QUESTIONNAIRES_ITEM_EXTENDED_EXTRACTION_PERIOD = "extended-extraction-period";
	String QUESTIONNAIRES_ITEM_DATA_SET_URL = "data-set-url";
	String QUESTIONNAIRES_PLACEHOLDER_PROJECT_IDENTIFIER = "{project-identifier-placeholder}";
	String QUESTIONNAIRES_PLACEHOLDER_DIC_IDENTIFIERS = "{dic-identifiers-placeholder}";
	String QUESTIONNAIRES_PLACEHOLDER_DMS_IDENTIFIER = "{dms-identifier-placeholder}";
	String QUESTIONNAIRES_PLACEHOLDER_FHIR_STORE_BASE_URL = "{fhir-store-base-url-placeholder}";

	String DATA_EXTRACTION_PERIOD_DEFAULT_VALUE = "P28D";
	String EXTENDED_DATA_EXTRACTION_PERIOD_DEFAULT_VALUE = "P5D";
}
