package org.jvalue.ods.adapterservice.datasource.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.jvalue.ods.adapterservice.adapter.Protocol;
import org.jvalue.ods.adapterservice.adapter.model.AdapterConfig;
import org.jvalue.ods.adapterservice.adapter.model.FormatConfig;
import org.jvalue.ods.adapterservice.adapter.model.ProtocolConfig;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.TypeDefs;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;


@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
@EqualsAndHashCode
@TypeDefs({
  @TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)
})
public class Datasource {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id") // referenced by embedded datasource config for format and protocol
  private Long id;

  @NotNull
  private DatasourceProtocol protocol;

  @NotNull
  private DatasourceFormat format;

  @NotNull
  private DatasourceMetadata metadata;

  @NotNull
  private DatasourceTrigger trigger;

  @Type(type = "jsonb")
  @Column(columnDefinition = "jsonb")
  private Object schema;

  @OneToMany(cascade = CascadeType.ALL, mappedBy = "datasource", fetch = FetchType.LAZY)
  @JsonIgnore
  @EqualsAndHashCode.Exclude // needed to avoid an endless loop because of a circular reference
  private Set<DataImport> dataImports;

  @JsonCreator
  public Datasource(
    @JsonProperty("protocol") DatasourceProtocol protocol,
    @JsonProperty("format") DatasourceFormat format,
    @JsonProperty("metadata") DatasourceMetadata metadata,
    @JsonProperty("trigger") DatasourceTrigger trigger,
    @JsonProperty("schema") Object schema) {
    this.protocol = protocol;
    this.format = format;
    this.metadata = metadata;
    this.trigger = trigger;
    this.schema = schema;
  }

  public AdapterConfig toAdapterConfig(RuntimeParameters runtimeParameters) {
    var parameters = fillQueryParameters(runtimeParameters);
    return new AdapterConfig(
      new ProtocolConfig(this.getProtocol().getType(), parameters),
      new FormatConfig(this.getFormat().getType(), this.getFormat().getParameters())
    );
  }

  protected Map<String, Object> fillQueryParameters(RuntimeParameters runtimeParameters) {
    if (!this.getProtocol().getType().equals(Protocol.HTTP)) {
      return this.getProtocol().getParameters();
    }

    Map<String, String> replacementParameters = new HashMap<>();

    //Add all default parameters to the replacement parameters map
    if (this.getProtocol().getParameters().containsKey("defaultParameters")) {
      @SuppressWarnings("unchecked")
      var defaultParams = (Map<String, String>) this.getProtocol().getParameters().get("defaultParameters");
      defaultParams.forEach(replacementParameters::put);
    }

    //Add all runtime parameters to the replacement parameters map
    if (runtimeParameters != null && runtimeParameters.getParameters() != null) {
      runtimeParameters.getParameters().forEach(replacementParameters::put);
    }

    String url = (String) this.getProtocol().getParameters().get("location");
    for (Map.Entry<String, String> parameter : replacementParameters.entrySet()) {
      url = url.replace("{" + parameter.getKey() + "}", parameter.getValue());
    }

    Map<String, Object> parameters = new HashMap<>(this.getProtocol().getParameters());
    parameters.put("location", url);
    return parameters;
  }
}
