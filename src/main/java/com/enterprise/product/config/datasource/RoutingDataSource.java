package com.enterprise.product.config.datasource;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class RoutingDataSource extends AbstractRoutingDataSource {

    public enum DataSourceType { MASTER, REPLICA }

    private static final ThreadLocal<DataSourceType> CONTEXT = new ThreadLocal<>();

    @Override
    protected Object determineCurrentLookupKey() {
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            return DataSourceType.REPLICA;
        }
        DataSourceType type = CONTEXT.get();
        return type != null ? type : DataSourceType.MASTER;
    }

    public static void setDataSourceType(DataSourceType type) { CONTEXT.set(type); }
    public static void clearDataSourceType() { CONTEXT.remove(); }
}
