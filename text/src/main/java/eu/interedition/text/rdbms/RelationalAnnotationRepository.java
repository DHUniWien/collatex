package eu.interedition.text.rdbms;

import com.google.common.base.Objects;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import eu.interedition.text.*;
import eu.interedition.text.mem.SimpleQName;
import eu.interedition.text.query.*;
import eu.interedition.text.util.AbstractAnnotationRepository;
import eu.interedition.text.util.SQL;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.jdbc.core.simple.SimpleJdbcTemplate;
import org.springframework.jdbc.support.incrementer.DataFieldMaxValueIncrementer;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.sql.DataSource;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.Reader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static eu.interedition.text.rdbms.RelationalQNameRepository.mapNameFrom;
import static eu.interedition.text.rdbms.RelationalQNameRepository.selectNameFrom;
import static eu.interedition.text.rdbms.RelationalTextRepository.mapTextFrom;
import static eu.interedition.text.rdbms.RelationalTextRepository.selectTextFrom;

public class RelationalAnnotationRepository extends AbstractAnnotationRepository implements InitializingBean {

  private DataSource dataSource;
  private DataFieldMaxValueIncrementerFactory incrementerFactory;
  private RelationalQNameRepository nameRepository;
  private RelationalTextRepository textRepository;

  private SimpleJdbcTemplate jt;
  private SimpleJdbcInsert annotationInsert;
  private SimpleJdbcInsert annotationLinkInsert;
  private SimpleJdbcInsert annotationLinkTargetInsert;
  private SAXParserFactory saxParserFactory;

  private int batchSize = 10000;
  private DataFieldMaxValueIncrementer annotationIdIncrementer;
  private DataFieldMaxValueIncrementer annotationLinkIdIncrementer;

  @Required
  public void setDataSource(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Required
  public void setIncrementerFactory(DataFieldMaxValueIncrementerFactory incrementerFactory) {
    this.incrementerFactory = incrementerFactory;
  }

  @Required
  public void setNameRepository(RelationalQNameRepository nameRepository) {
    this.nameRepository = nameRepository;
  }

  @Required
  public void setTextRepository(RelationalTextRepository textRepository) {
    this.textRepository = textRepository;
  }

  public void setBatchSize(int batchSize) {
    this.batchSize = batchSize;
  }

  public void afterPropertiesSet() throws Exception {
    this.jt = (dataSource == null ? null : new SimpleJdbcTemplate(dataSource));
    this.annotationInsert = (jt == null ? null : new SimpleJdbcInsert(dataSource).withTableName("text_annotation"));
    this.annotationLinkInsert = (jt == null ? null : new SimpleJdbcInsert(dataSource).withTableName("text_annotation_link"));
    this.annotationLinkTargetInsert = (jt == null ? null : new SimpleJdbcInsert(dataSource).withTableName("text_annotation_link_target"));

    this.saxParserFactory = SAXParserFactory.newInstance();
    this.saxParserFactory.setNamespaceAware(true);
    this.saxParserFactory.setValidating(false);

    this.annotationIdIncrementer = incrementerFactory.create("text_annotation");
    this.annotationLinkIdIncrementer = incrementerFactory.create("text_annotation_link");
  }

  public Iterable<Annotation> create(Iterable<Annotation> annotations) {
    final Set<QName> names = Sets.newHashSet();
    for (Annotation a : annotations) {
      names.add(a.getName());
    }
    final Map<QName, Long> nameIdIndex = Maps.newHashMapWithExpectedSize(names.size());
    for (QName name : nameRepository.get(names)) {
      nameIdIndex.put(name, ((RelationalQName) name).getId());
    }

    final List<Annotation> created = Lists.newArrayList();
    final List<SqlParameterSource> batchParameters = Lists.newArrayList();
    for (Annotation a : annotations) {
      final long id = annotationIdIncrementer.nextLongValue();
      final Long nameId = nameIdIndex.get(a.getName());
      final Range range = a.getRange();

      batchParameters.add(new MapSqlParameterSource()
              .addValue("id", id)
              .addValue("text", ((RelationalText) a.getText()).getId())
              .addValue("name", nameId)
              .addValue("range_start", range.getStart())
              .addValue("range_end", range.getEnd()));

      final RelationalAnnotation ra = new RelationalAnnotation();
      ra.setId(id);
      ra.setText(a.getText());
      ra.setName(new RelationalQName(nameId, a.getName()));
      ra.setRange(range);
      created.add(ra);
    }

    annotationInsert.executeBatch(batchParameters.toArray(new SqlParameterSource[batchParameters.size()]));

    return created;
  }

  public Iterable<AnnotationLink> createLink(Iterable<QName> names) {
    final Map<QName, Long> nameIdIndex = Maps.newHashMap();
    for (QName n : nameRepository.get(Sets.newHashSet(names))) {
      nameIdIndex.put(n, ((RelationalQName) n).getId());
    }

    final List<AnnotationLink> created = Lists.newArrayList();
    final List<SqlParameterSource> batchParameters = Lists.newArrayList();
    for (QName n : names) {
      final long id = annotationLinkIdIncrementer.nextLongValue();
      final Long nameId = nameIdIndex.get(n);

      batchParameters.add(new MapSqlParameterSource()
              .addValue("id", id)
              .addValue("name", nameId));

      created.add(new RelationalAnnotationLink(id, new RelationalQName(nameId, n)));
    }

    annotationLinkInsert.executeBatch(batchParameters.toArray(new SqlParameterSource[batchParameters.size()]));
    return created;
  }

  public void delete(Criterion criterion) {
    final ArrayList<Object> parameters = new ArrayList<Object>();
    final List<Object[]> batchParameters = Lists.newArrayListWithCapacity(batchSize);

    jt.query(buildAnnotationFinderSQL("select a.id as a_id", parameters, criterion).toString(), new RowMapper<Void>() {
      public Void mapRow(ResultSet rs, int rowNum) throws SQLException {
        batchParameters.add(new Object[]{rs.getInt("a_id")});

        if (rs.isLast() || (batchParameters.size() % batchSize) == 0) {
          jt.batchUpdate("delete from text_annotation where id = ?", batchParameters);
          batchParameters.clear();
        }

        return null;
      }
    }, parameters.toArray(new Object[parameters.size()]));
  }

  public void deleteLinks(Criterion criterion) {
    final ArrayList<Object> parameters = new ArrayList<Object>();
    final List<Object[]> batchParameters = Lists.newArrayListWithCapacity(batchSize);
    jt.query(buildAnnotationLinkFinderSQL("select distinct al.id as al_id", parameters, criterion).toString(), new RowMapper<Void>() {
      public Void mapRow(ResultSet rs, int rowNum) throws SQLException {
        batchParameters.add(new Object[]{rs.getInt("al_id")});

        if (rs.isLast() || (batchParameters.size() % batchSize) == 0) {
          jt.batchUpdate("delete from text_annotation_link where id = ?", batchParameters);
          batchParameters.clear();
        }

        return null;
      }
    }, parameters.toArray(new Object[parameters.size()]));
  }

  @SuppressWarnings("unchecked")
  public void add(AnnotationLink to, Set<Annotation> toAdd) {
    if (toAdd == null || toAdd.isEmpty()) {
      return;
    }
    final long linkId = ((RelationalAnnotationLink) to).getId();
    final List<Map<String, Object>> psList = new ArrayList<Map<String, Object>>(toAdd.size());
    for (RelationalAnnotation annotation : Iterables.filter(toAdd, RelationalAnnotation.class)) {
      final Map<String, Object> ps = new HashMap<String, Object>(2);
      ps.put("link", linkId);
      ps.put("target", annotation.getId());
      psList.add(ps);
    }
    annotationLinkTargetInsert.executeBatch(psList.toArray(new Map[psList.size()]));
  }

  public void remove(AnnotationLink from, Set<Annotation> toRemove) {
    if (toRemove == null || toRemove.isEmpty()) {
      return;
    }

    final StringBuilder sql = new StringBuilder("delete from text_annotation_link_target where link = ? and ");

    final List<Object> params = new ArrayList<Object>(toRemove.size() + 1);
    params.add(((RelationalAnnotationLink) from).getId());

    final Set<RelationalAnnotation> annotations = Sets.newHashSet(Iterables.filter(toRemove, RelationalAnnotation.class));
    sql.append("target in (");
    for (Iterator<RelationalAnnotation> it = annotations.iterator(); it.hasNext(); ) {
      params.add(it.next().getId());
      sql.append("?").append(it.hasNext() ? ", " : "");
    }
    sql.append(")");

    jt.update(sql.toString(), params.toArray(new Object[params.size()]));
  }

  public Iterable<Annotation> find(Criterion criterion) {
    List<Object> parameters = Lists.newArrayList();

    final StringBuilder sql = buildAnnotationFinderSQL(new StringBuilder("select ").
            append(selectAnnotationFrom("a")).append(", ").
            append(selectNameFrom("n")).append(", ").
            append(selectTextFrom("t")).toString(), parameters, criterion);
    sql.append(" order by n.id");

    return Sets.newTreeSet(jt.query(sql.toString(), new RowMapper<Annotation>() {
      private RelationalQName currentName;

      public Annotation mapRow(ResultSet rs, int rowNum) throws SQLException {
        if (currentName == null || currentName.getId() != rs.getInt("n_id")) {
          currentName = mapNameFrom(rs, "n");
        }
        return mapAnnotationFrom(rs, mapTextFrom(rs, "t"), currentName, "a");
      }
    }, parameters.toArray(new Object[parameters.size()])));
  }

  public Map<AnnotationLink, Set<Annotation>> findLinks(Criterion criterion) {

    // FIXME: two-pass query: first select link ids; then fetch complete links

    final List<Object> params = new ArrayList<Object>();
    final StringBuilder sql = buildAnnotationLinkFinderSQL(new StringBuilder("select ")
            .append("al.id as al_id, ")
            .append(selectAnnotationFrom("a")).append(", ")
            .append(selectTextFrom("t")).append(", ")
            .append(selectNameFrom("aln")).append(", ")
            .append(selectNameFrom("an")).toString(), params, criterion);
    sql.append(" order by al.id, t.id, an.id, a.id");

    final Map<AnnotationLink, Set<Annotation>> annotationLinks = new HashMap<AnnotationLink, Set<Annotation>>();

    jt.query(sql.toString(), new RowMapper<Void>() {
      private RelationalAnnotationLink currentLink;
      private RelationalText currentText;
      private RelationalQName currentAnnotationName;

      public Void mapRow(ResultSet rs, int rowNum) throws SQLException {
        final int annotationLinkId = rs.getInt("al_id");
        final int textId = rs.getInt("t_id");
        final int annotationNameId = rs.getInt("an_id");

        if (currentLink == null || currentLink.getId() != annotationLinkId) {
          currentLink = new RelationalAnnotationLink(annotationLinkId, mapNameFrom(rs, "aln"));
        }
        if (currentText == null || currentText.getId() != textId) {
          currentText = RelationalTextRepository.mapTextFrom(rs, "t");
        }
        if (currentAnnotationName == null || currentAnnotationName.getId() != annotationNameId) {
          currentAnnotationName = mapNameFrom(rs, "an");
        }

        Set<Annotation> members = annotationLinks.get(currentLink);
        if (members == null) {
          annotationLinks.put(currentLink, members = new TreeSet<Annotation>());
        }
        members.add(mapAnnotationFrom(rs, currentText, currentAnnotationName, "a"));

        return null;
      }

    }, params.toArray(new Object[params.size()]));

    return annotationLinks;
  }

  private StringBuilder buildAnnotationFinderSQL(String selectClause, List<Object> parameters, Criterion criterion) {
    final StringBuilder sql = new StringBuilder(selectClause);
    sql.append(" from text_annotation a");
    sql.append(" join text_qname n on a.name = n.id");
    sql.append(" join text_content t on a.text = t.id");
    sql.append(" where ");

    toWhereClause(criterion, sql, parameters);

    return sql;
  }

  private StringBuilder buildAnnotationLinkFinderSQL(String selectClause, List<Object> params, Criterion criterion) {
    final StringBuilder sql = new StringBuilder(selectClause);
    sql.append(" from text_annotation_link_target alt");
    sql.append(" join text_annotation_link al on alt.link = al.id");
    sql.append(" join text_qname aln on al.name = aln.id");
    sql.append(" join text_annotation a on alt.target = a.id");
    sql.append(" join text_qname an on a.name = an.id");
    sql.append(" join text_content t on a.text = t.id");
    sql.append(" where ");

    toWhereClause(criterion, sql, params);

    return sql;
  }

  protected void toSQL(Operator op, StringBuilder sql, Collection<Object> sqlParameters) {
    final List<Criterion> operands = op.getOperands();
    if (operands.isEmpty()) {
      return;
    }
    sql.append("(");
    for (Iterator<Criterion> pIt = operands.iterator(); pIt.hasNext(); ) {
      toWhereClause(pIt.next(), sql, sqlParameters);
      if (pIt.hasNext()) {
        sql.append(" ").append(sqlOperator(op)).append(" ");
      }
    }
    sql.append(")");
  }

  protected String sqlOperator(Operator op) {
    if (op instanceof AndOperator) {
      return "and";
    } else if (op instanceof OrOperator) {
      return "or";
    } else {
      throw new IllegalArgumentException();
    }
  }

  protected void toSQL(NotOperator predicate, StringBuilder sql, Collection<Object> sqlParameters) {
    sql.append("(NOT ");
    toWhereClause(predicate.getOperand(), sql, sqlParameters);
    sql.append(")");
  }

  protected void toSQL(AnnotationLinkNameCriterion predicate, StringBuilder sql, Collection<Object> sqlParameters) {
    sql.append("(al.name = ?)");
    sqlParameters.add(((RelationalQName)nameRepository.get(predicate.getName())).getId());
  }

  protected void toSQL(AnnotationIdentityCriterion predicate, StringBuilder sql, Collection<Object> sqlParameters) {
    sql.append("(a.id = ?)");
    sqlParameters.add(((RelationalAnnotation) predicate.getAnnotation()).getId());
  }

  protected void toSQL(AnnotationNameCriterion predicate, StringBuilder sql, Collection<Object> sqlParameters) {
    sql.append("(a.name = ?)");
    sqlParameters.add(((RelationalQName)nameRepository.get(predicate.getName())).getId());
  }

  protected void toSQL(TextCriterion predicate, StringBuilder sql, Collection<Object> sqlParameters) {
    sql.append("(a.text = ?)");
    sqlParameters.add(((RelationalText) predicate.getText()).getId());
  }

  protected void toSQL(RangeCriterion predicate, StringBuilder sql, Collection<Object> sqlParameters) {
    final Range range = predicate.getRange();
    sql.append("(a.range_start < ? and a.range_end > ?)");
    sqlParameters.add(range.getEnd());
    sqlParameters.add(range.getStart());
  }

  protected void toSQL(AnyCriterion predicate, StringBuilder sql, Collection<Object> sqlParameters) {
    sql.append("(1 = 1)");
  }

  protected void toSQL(NoneCriterion predicate, StringBuilder sql, Collection<Object> sqlParameters) {
    sql.append("(1 <> 1)");
  }

  protected void toWhereClause(Criterion criterion, StringBuilder sql, Collection<Object> sqlParameters) {
    if (criterion instanceof Operator) {
      toSQL((Operator) criterion, sql, sqlParameters);
    } else if (criterion instanceof AnnotationNameCriterion) {
      toSQL((AnnotationNameCriterion) criterion, sql, sqlParameters);
    } else if (criterion instanceof TextCriterion) {
      toSQL((TextCriterion) criterion, sql, sqlParameters);
    } else if (criterion instanceof AnnotationLinkNameCriterion) {
      toSQL((AnnotationLinkNameCriterion) criterion, sql, sqlParameters);
    } else if (criterion instanceof RangeCriterion) {
      toSQL((RangeCriterion) criterion, sql, sqlParameters);
    } else if (criterion instanceof AnnotationIdentityCriterion) {
      toSQL((AnnotationIdentityCriterion) criterion, sql, sqlParameters);
    } else if (criterion instanceof NotOperator) {
      toSQL((NotOperator) criterion, sql, sqlParameters);
    } else if (criterion instanceof AnyCriterion) {
      toSQL((AnyCriterion) criterion, sql, sqlParameters);
    } else if (criterion instanceof NoneCriterion) {
      toSQL((NoneCriterion) criterion, sql, sqlParameters);
    } else {
      throw new IllegalArgumentException(Objects.toStringHelper(criterion).toString());
    }
  }

  public SortedSet<QName> names(Text text) {
    final StringBuilder namesSql = new StringBuilder("select distinct ");
    namesSql.append(selectNameFrom("n"));
    namesSql.append(" from text_qname n join text_annotation a on a.name = n.id where a.text = ?");
    final SortedSet<QName> names = Sets.newTreeSet(jt.query(namesSql.toString(), new RowMapper<QName>() {

      public QName mapRow(ResultSet rs, int rowNum) throws SQLException {
        return mapNameFrom(rs, "n");
      }
    }, ((RelationalText) text).getId()));

    if (names.isEmpty() && text.getType() == Text.Type.XML) {
      try {
        textRepository.read(text, new TextRepository.TextReader() {
          public void read(Reader content, int contentLength) throws IOException {
            if (contentLength == 0) {
              return;
            }
            try {
              saxParserFactory.newSAXParser().parse(new InputSource(content), new DefaultHandler() {
                @Override
                public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
                  names.add(new SimpleQName(uri, localName));
                }
              });
            } catch (SAXException e) {
              throw Throwables.propagate(e);
            } catch (ParserConfigurationException e) {
              throw Throwables.propagate(e);
            }
          }
        });
      } catch (IOException e) {
        throw Throwables.propagate(e);
      }
    }

    return names;
  }

  public static String selectAnnotationFrom(String tableName) {
    return SQL.select(tableName, "id", "range_start", "range_end");
  }


  public static RelationalAnnotation mapAnnotationFrom(ResultSet rs, RelationalText text, RelationalQName name, String tableName) throws SQLException {
    final RelationalAnnotation ra = new RelationalAnnotation();
    ra.setId(rs.getInt(tableName + "_id"));
    ra.setName(name);
    ra.setText(text);
    ra.setRange(new Range(rs.getInt(tableName + "_range_start"), rs.getInt(tableName + "_range_end")));
    return ra;
  }
}