package liquibase.ext.hibernate.snapshot;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import liquibase.exception.DatabaseException;
import liquibase.ext.hibernate.database.HibernateDatabase;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.InvalidExampleException;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Catalog;
import liquibase.structure.core.ForeignKey;
import liquibase.structure.core.ForeignKeyConstraintType;
import liquibase.structure.core.Index;
import liquibase.structure.core.Schema;
import liquibase.structure.core.Table;

import org.hibernate.cfg.Configuration;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.spi.Mapping;
import org.hibernate.mapping.Column;
import org.hibernate.type.Type;

public class ForeignKeySnapshotGenerator extends HibernateSnapshotGenerator {

  public ForeignKeySnapshotGenerator() {
    super(ForeignKey.class, new Class[] { Table.class });
  }

  @Override
  protected DatabaseObject snapshotObject(DatabaseObject example, DatabaseSnapshot snapshot) throws DatabaseException,
      InvalidExampleException {
    return example;
  }

  @Override
  protected void addTo(DatabaseObject foundObject, DatabaseSnapshot snapshot) throws DatabaseException,
      InvalidExampleException {
    if (!snapshot.getSnapshotControl().shouldInclude(ForeignKey.class)) {
      return;
    }
    if (foundObject instanceof Table) {
      Table table = (Table) foundObject;
      HibernateDatabase database = (HibernateDatabase) snapshot.getDatabase();
      Dialect dialect = database.getDialect();
      Configuration cfg = database.getConfiguration();
      Iterator<org.hibernate.mapping.Table> tableMappings = cfg.getTableMappings();
      while (tableMappings.hasNext()) {
        org.hibernate.mapping.Table hibernateTable = (org.hibernate.mapping.Table) tableMappings.next();
        Iterator<?> fkIterator = hibernateTable.getForeignKeyIterator();
        while (fkIterator.hasNext()) {
          org.hibernate.mapping.ForeignKey hibernateForeignKey = (org.hibernate.mapping.ForeignKey) fkIterator.next();
          Table currentTable = new Table().setName(hibernateTable.getName());
          currentTable.setSchema(hibernateTable.getCatalog(), hibernateTable.getSchema());
          org.hibernate.mapping.Table hibernateReferencedTable = hibernateForeignKey.getReferencedTable();
          Table referencedTable = new Table().setName(hibernateReferencedTable.getName());
          referencedTable.setSchema(hibernateReferencedTable.getCatalog(), hibernateReferencedTable.getSchema());
          if (hibernateForeignKey.isPhysicalConstraint()) {
            ForeignKey fk = new ForeignKey();
            fk.setName(hibernateForeignKey.getName());
            fk.setPrimaryKeyTable(referencedTable);
            for (Object column : hibernateForeignKey.getColumns()) {
              fk.addForeignKeyColumn(((org.hibernate.mapping.Column) column).getName());
            }
            fk.setForeignKeyTable(currentTable);
            for (Object column : hibernateForeignKey.getReferencedColumns()) {
              fk.addPrimaryKeyColumn(((org.hibernate.mapping.Column) column).getName());
            }
            if (fk.getPrimaryKeyColumns() == null || fk.getPrimaryKeyColumns().isEmpty()) {
              for (Object column : hibernateReferencedTable.getPrimaryKey().getColumns()) {
                fk.addPrimaryKeyColumn(((org.hibernate.mapping.Column) column).getName());
              }
            }
            
            if (dialect.supportsCascadeDelete() && hibernateForeignKey.isCascadeDeleteEnabled()) {
              ForeignKeyConstraintType deleteRule = ForeignKeyConstraintType.importedKeyCascade;
              fk.setDeleteRule(deleteRule);
            }

            fk.setDeferrable(false);
            fk.setInitiallyDeferred(false);

            // Index index = new Index();
            // index.setName("IX_" + fk.getName());
            // index.setTable(fk.getForeignKeyTable());
            // index.setColumns(fk.getForeignKeyColumns());
            // fk.setBackingIndex(index);
            // table.getIndexes().add(index);

            if (currentTable.equals(table)) {
              table.getOutgoingForeignKeys().add(fk);
            }
          }
        }
      }
    }
  }

}
