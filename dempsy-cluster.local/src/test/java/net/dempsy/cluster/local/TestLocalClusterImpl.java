package net.dempsy.cluster.local;

import net.dempsy.cluster.TestClusterImpls;

public class TestLocalClusterImpl extends TestClusterImpls {

    public TestLocalClusterImpl() {
        super(new LocalClusterSessionFactory());
    }
}
