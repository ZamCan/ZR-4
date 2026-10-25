package com.zamcan.zr4.engine

import org.junit.Test

class RfAnalysisPipelineTest {
    @Test fun pipelineContractIsVersioned() {
        org.junit.Assert.assertEquals("zr4-real-spectrum-pipeline-1", RfAnalysisPipeline.SPECTRUM_ALGORITHM_VERSION)
    }
}
