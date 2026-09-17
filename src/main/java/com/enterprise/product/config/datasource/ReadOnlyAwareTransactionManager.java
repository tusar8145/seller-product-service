package com.enterprise.product.config.datasource;

import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;

public class ReadOnlyAwareTransactionManager extends JpaTransactionManager {

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        if (definition.isReadOnly()) {
            RoutingDataSource.setDataSourceType(RoutingDataSource.DataSourceType.REPLICA);
        } else {
            RoutingDataSource.setDataSourceType(RoutingDataSource.DataSourceType.MASTER);
        }
        super.doBegin(transaction, definition);
    }

    @Override
    protected void doCleanupAfterCompletion(Object transaction) {
        super.doCleanupAfterCompletion(transaction);
        RoutingDataSource.clearDataSourceType();
    }
}
