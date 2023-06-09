package cz.pwf.filenet;

import cz.notix.zeebe.job.ZeebeInit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
public class ZeebeinitRuntimeTest {

    @Autowired
    ZeebeInit zeebeInit;

    @Test
    public void zeebeInitContextTest() {
        assert zeebeInit != null;
    }

}
