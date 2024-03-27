package de.medizininformatik_initiative.process.data_sharing.bpe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.process.data_sharing.DataSharingProcessPluginDefinition;
import dev.dsf.bpe.v1.ProcessPluginDefinition;

public class DataSharingProcessPluginDefinitionTest
{
	@Test
	public void testResourceLoading()
	{
		ProcessPluginDefinition definition = new DataSharingProcessPluginDefinition();
		Map<String, List<String>> resourcesByProcessId = definition.getFhirResourcesByProcessId();

		var coordinate = resourcesByProcessId.get(ConstantsDataSharing.PROCESS_NAME_FULL_COORDINATE_DATA_SHARING);
		assertNotNull(coordinate);
		assertEquals(9, coordinate.stream().filter(this::exists).count());

		var execute = resourcesByProcessId.get(ConstantsDataSharing.PROCESS_NAME_FULL_EXECUTE_DATA_SHARING);
		assertNotNull(execute);
		assertEquals(10, execute.stream().filter(this::exists).count());

		var merge = resourcesByProcessId.get(ConstantsDataSharing.PROCESS_NAME_FULL_MERGE_DATA_SHARING);
		assertNotNull(merge);
		assertEquals(14, merge.stream().filter(this::exists).count());
	}

	private boolean exists(String file)
	{
		return getClass().getClassLoader().getResourceAsStream(file) != null;
	}
}
