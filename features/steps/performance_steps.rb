# frozen_string_literal: true

def execute_command(action, scenario_name = '', scenario_metadata = '')

  address = if Maze.config.farm == :bb
              if Maze.config.aws_public_ip
                Maze.public_address
              else
                'local:9339'
              end
            else
              'bs-local.com:9339'
            end

  command = {
    action: action,
    cucumber_scenario_name: Maze.scenario.name,
    cucumber_scenario_location: Maze.scenario.location,
    scenario_name: scenario_name,
    scenario_metadata: scenario_metadata,
    endpoint: "http://#{address}/traces",
    index: Maze::Server.commands.size_remaining.to_s
  }
  Maze::Server.commands.add command
end

When('I run {string}') do |scenario_name|
  execute_command 'run_scenario', scenario_name
end

When('I run {string} configured as {string}') do |scenario_name, scenario_metadata|
  execute_command 'run_scenario', scenario_name, scenario_metadata
end

When('I configure bugsnag {string} to {string}') do |key, value|
  execute_command 'configure_bugsnag', key, value
end

When('I configure scenario {string} to {string}') do |key, value|
  execute_command 'configure_scenario', key, value
end

When('I start bugsnag') do
  execute_command 'start_bugsnag'
end

When('I run the loaded scenario') do
  execute_command 'run_loaded_scenario'
end

When('I load scenario {string}') do |scenario_name|
  execute_command 'load_scenario', scenario_name
end

When('I invoke {string}') do |function|
  execute_command 'invoke', function
end

When('I invoke {string} for {string}') do |function, metadata|
  execute_command 'invoke', function, metadata
end

Then('I received no span named {string}') do |span_name|
  spans = spans_from_request_list(Maze::Server.list_for('traces'))
  named_spans = spans.select { |s| s['name'].eql?(span_name) }

  Maze.check.equal(0, named_spans.size, "found #{named_spans.size} spans named #{span_name}")
end

Then("the {string} span field {string} is stored as the value {string}") do |span_name, field, key|
  spans = spans_from_request_list(Maze::Server.list_for('traces'))
  named_spans = spans.select { |s| s['name'].eql?(span_name) }

  raise Test::Unit::AssertionFailedError.new "no span named #{span_name} found" if named_spans.empty?
  raise Test::Unit::AssertionFailedError.new "found #{named_spans.size} spans named #{span_name}, expected exactly one" unless named_spans.size == 1

  value = Maze::Helper.read_key_path(named_spans[0], field)
  Maze::Store.values[key] = value.dup
end

Then("the {string} span field {string} equals the stored value {string}") do |span_name, field, stored_key|
  spans = spans_from_request_list(Maze::Server.list_for('traces'))
  named_spans = spans.select { |s| s['name'].eql?(span_name) }

  raise Test::Unit::AssertionFailedError.new "no span named #{span_name} found" if named_spans.empty?
  raise Test::Unit::AssertionFailedError.new "found #{named_spans.size} spans named #{span_name}, expected exactly one" unless named_spans.size == 1

  value = Maze::Helper.read_key_path(named_spans[0], field)
  Maze.check.equal(Maze::Store.values[stored_key], value)
end

def get_attribute_value(attribute_obj, type)
  # OTLP uses 'intValue', 'boolValue', 'doubleValue'
  real_type = case type
              when 'integer' then 'int'
              when 'boolean' then 'bool'
              when 'float' then 'double'
              else type
              end
  attribute_obj.dig 'value', "#{real_type}Value"
end

Then('the {string} span {word} attribute {string} is greater than {float}') do |span_name, type, attribute, expected|
  spans = spans_from_request_list(Maze::Server.list_for('traces'))
  found_spans = spans.find_all { |span| span['name'].eql?(span_name) }
  raise Test::Unit::AssertionFailedError.new "No spans were found with the name #{span_name}" if found_spans.empty?

  found_spans.each do |span|
    attributes = span['attributes']
    attribute_obj = attributes.find { |a| a['key'] == attribute }
    raise Test::Unit::AssertionFailedError.new "No attribute named #{attribute} was found in span #{span_name}" if attribute_obj.nil?

    value = get_attribute_value(attribute_obj, type)
    raise Test::Unit::AssertionFailedError.new "Attribute #{attribute} in span #{span_name} is not of type #{type}" if value.nil?

    Maze.check.operator value.to_f, :>, expected.to_f,
                          "The span '#{span_name}' attribute '#{attribute}' (#{value}) is not greater than '#{expected}'"
  end
end

Then('the {string} span {word} attribute {string} is less than or equal to {float}') do |span_name, type, attribute, expected|
  spans = spans_from_request_list(Maze::Server.list_for('traces'))
  found_spans = spans.find_all { |span| span['name'].eql?(span_name) }
  raise Test::Unit::AssertionFailedError.new "No spans were found with the name #{span_name}" if found_spans.empty?

  found_spans.each do |span|
    attributes = span['attributes']
    attribute_obj = attributes.find { |a| a['key'] == attribute }
    raise Test::Unit::AssertionFailedError.new "No attribute named #{attribute} was found in span #{span_name}" if attribute_obj.nil?

    value = get_attribute_value(attribute_obj, type)
    raise Test::Unit::AssertionFailedError.new "Attribute #{attribute} in span #{span_name} is not of type #{type}" if value.nil?

    Maze.check.operator value.to_f, :<=, expected.to_f,
                          "The span '#{span_name}' attribute '#{attribute}' (#{value}) is not less than or equal to '#{expected}'"
  end
end

Then('the {string} span {word} attribute {string} is greater than or equal to {float}') do |span_name, type, attribute, expected|
  spans = spans_from_request_list(Maze::Server.list_for('traces'))
  found_spans = spans.find_all { |span| span['name'].eql?(span_name) }
  raise Test::Unit::AssertionFailedError.new "No spans were found with the name #{span_name}" if found_spans.empty?

  found_spans.each do |span|
    attributes = span['attributes']
    attribute_obj = attributes.find { |a| a['key'] == attribute }
    raise Test::Unit::AssertionFailedError.new "No attribute named #{attribute} was found in span #{span_name}" if attribute_obj.nil?

    value = get_attribute_value(attribute_obj, type)
    raise Test::Unit::AssertionFailedError.new "Attribute #{attribute} in span #{span_name} is not of type #{type}" if value.nil?

    Maze.check.operator value.to_f, :>=, expected.to_f,
                          "The span '#{span_name}' attribute '#{attribute}' (#{value}) is not greater than or equal to '#{expected}'"
  end
end

Then('the {string} span {word} attribute {string} is less than or equal to the {word} attribute {string}') do |span_name, type1, attr1, type2, attr2|
  spans = spans_from_request_list(Maze::Server.list_for('traces'))
  found_spans = spans.find_all { |span| span['name'].eql?(span_name) }
  raise Test::Unit::AssertionFailedError.new "No spans were found with the name #{span_name}" if found_spans.empty?

  found_spans.each do |span|
    attributes = span['attributes']
    val1_obj = attributes.find { |a| a['key'] == attr1 }
    val2_obj = attributes.find { |a| a['key'] == attr2 }

    raise Test::Unit::AssertionFailedError.new "Attribute #{attr1} not found in span #{span_name}" if val1_obj.nil?
    raise Test::Unit::AssertionFailedError.new "Attribute #{attr2} not found in span #{span_name}" if val2_obj.nil?

    val1 = get_attribute_value(val1_obj, type1)
    val2 = get_attribute_value(val2_obj, type2)

    Maze.check.operator val1.to_f, :<=, val2.to_f,
                          "The span '#{span_name}' attribute '#{attr1}' (#{val1}) is not <= '#{attr2}' (#{val2})"
  end
end

Then('the {string} span has no {string} attribute') do |span_name, attribute|
  spans = spans_from_request_list(Maze::Server.list_for('traces'))
  found_spans = spans.find_all { |span| span['name'].eql?(span_name) }
  raise Test::Unit::AssertionFailedError.new "No spans were found with the name #{span_name}" if found_spans.empty?

  found_spans.each do |span|
    attributes = span['attributes']
    attribute_obj = attributes.find { |a| a['key'] == attribute }
    Maze.check.nil(attribute_obj, "Span '#{span_name}' should not have attribute '#{attribute}'")
  end
end

Then('the {string} span has {word} attribute named {string}') do |span_name, attribute_type, attribute|
  spans = spans_from_request_list(Maze::Server.list_for('traces'))
  found_spans = spans.find_all { |span| span['name'].eql?(span_name) }
  raise Test::Unit::AssertionFailedError.new "No spans were found with the name #{span_name}" if found_spans.empty?
  raise Test::Unit::AssertionFailedError.new "found #{found_spans.size} spans named #{span_name}, expected exactly one" unless found_spans.size == 1

  attributes = found_spans.first['attributes']
  attribute_obj = attributes.find { |a| a['key'] == attribute }
  raise Test::Unit::AssertionFailedError.new "No attribute named #{attribute} was found in span #{span_name}" if attribute_obj.nil?

  value = get_attribute_value(attribute_obj, attribute_type)

  Maze.check.not_nil value
end

Then('the {string} span string attribute {string} equals {string}') do |span_name, attribute, expected|
  spans = spans_from_request_list(Maze::Server.list_for('traces'))
  found_spans = spans.find_all { |span| span['name'].eql?(span_name) }
  raise Test::Unit::AssertionFailedError.new "No spans were found with the name #{span_name}" if found_spans.empty?

  found_spans.each do |span|
    attributes = span['attributes']
    attribute_obj = attributes.find { |a| a['key'] == attribute }
    raise Test::Unit::AssertionFailedError.new "No attribute named #{attribute} was found in span #{span_name}" if attribute_obj.nil?

    value = attribute_obj['value']['stringValue']
    Maze.check.equal(expected, value)
  end
end

Then('the {string} span boolean attribute {string} is {word}') do |span_name, attribute, expected|
  spans = spans_from_request_list(Maze::Server.list_for('traces'))
  found_spans = spans.find_all { |span| span['name'].eql?(span_name) }
  raise Test::Unit::AssertionFailedError.new "No spans were found with the name #{span_name}" if found_spans.empty?

  expected_bool = (expected == 'true')

  found_spans.each do |span|
    attributes = span['attributes']
    attribute_obj = attributes.find { |a| a['key'] == attribute }
    raise Test::Unit::AssertionFailedError.new "No attribute named #{attribute} was found in span #{span_name}" if attribute_obj.nil?

    value = get_attribute_value(attribute_obj, 'boolean')
    Maze.check.equal(expected_bool, value)
  end
end

# Ambiguous steps removed to favor maze-runner definitions
Then('a span bool attribute {string} is {word}') do |attribute, expected|
  spans = spans_from_request_list(Maze::Server.list_for('traces'))
  expected_bool = (expected == 'true')
  found = spans.find do |span|
    attr_obj = span['attributes'].find { |a| a['key'] == attribute }
    attr_obj && attr_obj['value']['boolValue'] == expected_bool
  end
  raise Test::Unit::AssertionFailedError.new "No span found where attribute #{attribute} is #{expected}" if found.nil?
end

Then(/^a span (integer|float|bool|string) attribute "([^"]+)" is greater than or equal to (\d+\.?\d*)$/) do |type, attribute, expected|
  spans = spans_from_request_list(Maze::Server.list_for('traces'))
  found = spans.find do |span|
    attr_obj = span['attributes'].find { |a| a['key'] == attribute }
    if attr_obj
      value = get_attribute_value(attr_obj, type)
      value.to_f >= expected.to_f
    else
      false
    end
  end
  raise Test::Unit::AssertionFailedError.new "No span found where attribute #{attribute} is >= #{expected}" if found.nil?
end

Then(/^a span (integer|float|bool|string) attribute "([^"]+)" is less than or equal to (\d+\.?\d*)$/) do |type, attribute, expected|
  spans = spans_from_request_list(Maze::Server.list_for('traces'))
  found = spans.find do |span|
    attr_obj = span['attributes'].find { |a| a['key'] == attribute }
    if attr_obj
      value = get_attribute_value(attr_obj, type)
      value.to_f <= expected.to_f
    else
      false
    end
  end
  raise Test::Unit::AssertionFailedError.new "No span found where attribute #{attribute} is <= #{expected}" if found.nil?
end

Then('a span {word} attribute {string} is less than or equal to span {word} attribute {string}') do |type1, attr1, type2, attr2|
  spans = spans_from_request_list(Maze::Server.list_for('traces'))
  found = spans.find do |span|
    val1_obj = span['attributes'].find { |a| a['key'] == attr1 }
    val2_obj = span['attributes'].find { |a| a['key'] == attr2 }
    if val1_obj && val2_obj
      val1 = get_attribute_value(val1_obj, type1)
      val2 = get_attribute_value(val2_obj, type2)
      val1.to_f <= val2.to_f
    else
      false
    end
  end
  raise Test::Unit::AssertionFailedError.new "No span found where attribute #{attr1} <= #{attr2}" if found.nil?
end

Then('every span attribute {string} does not exist') do |attribute|
  spans = spans_from_request_list(Maze::Server.list_for('traces'))
  spans.each do |span|
    attr_obj = span['attributes'].find { |a| a['key'] == attribute }
    raise Test::Unit::AssertionFailedError.new "Attribute #{attribute} exists in a span" unless attr_obj.nil?
  end
end

Then('every span integer attribute {string} matches the regex {string}') do |attribute, pattern|
  regex = Regexp.new(pattern)
  spans = spans_from_request_list(Maze::Server.list_for('traces'))
  spans.each do |span|
    attr_obj = span['attributes'].find { |a| a['key'] == attribute }
    raise Test::Unit::AssertionFailedError.new "No attribute named #{attribute} found" if attr_obj.nil?
    value = attr_obj['value']['intValue']
    Maze.check.match(regex, value)
  end
end

Then('a span field {string} is empty') do |field|
  spans = spans_from_request_list(Maze::Server.list_for('traces'))
  found = spans.find { |s|
    val = Maze::Helper.read_key_path(s, field)
    val.nil? || val.empty?
  }
  raise Test::Unit::AssertionFailedError.new "No span found where #{field} is empty" if found.nil?
end

Then('every app_session span string attribute {string} equals {string}') do |attribute, expected|
  spans = spans_from_request_list(Maze::Server.list_for('traces'))
  spans.each do |span|
    # Only check app_session spans
    category = span['attributes'].find { |a| a['key'] == 'bugsnag.span.category' }
    next unless category && category['value']['stringValue'] == 'app_session'

    attr_obj = span['attributes'].find { |a| a['key'] == attribute }
    raise Test::Unit::AssertionFailedError.new "Attribute #{attribute} not found in one of the spans" if attr_obj.nil?
    Maze.check.equal(expected, attr_obj['value']['stringValue'])
  end
end

Then('the {string} span field {string} is empty') do |span_name, field|
  spans = spans_from_request_list(Maze::Server.list_for('traces'))
  named_spans = spans.select { |s| s['name'].eql?(span_name) }
  raise Test::Unit::AssertionFailedError.new "no span named #{span_name} found" if named_spans.empty?

  named_spans.each do |span|
    value = Maze::Helper.read_key_path(span, field)
    Maze.check.true(value.nil? || value.empty?, "Field #{field} in span #{span_name} should be empty, but was #{value}")
  end
end

When("I relaunch the app after shutdown") do
  max_attempts = 20
  attempts = 0
  manager = Maze::Api::Appium::AppManager.new
  state = manager.state
  until (attempts >= max_attempts) || state == :not_running
    attempts += 1
    state = manager.state
    sleep 0.5
  end
  $logger.warn "App state #{state} instead of not_running after 10s" unless state == :not_running

  manager.activate
end

def spans_from_request_list list
  return list.all
             .flat_map { |req| req[:body]['resourceSpans'] }
             .flat_map { |r| r['scopeSpans'] }
             .flat_map { |s| s['spans'] }
             .select { |s| !s.nil? }
end

Then('the {string} span took at least {int} {word}') do |span_name, min_duration, unit|
  spans = spans_from_request_list(Maze::Server.list_for('traces'))
  named_spans = spans.select { |s| s['name'].eql?(span_name) }

  raise Test::Unit::AssertionFailedError.new "no span named #{span_name} found" if named_spans.empty?
  raise Test::Unit::AssertionFailedError.new "found #{named_spans.size} spans named #{span_name}, expected exactly one" unless named_spans.size == 1

  end_time_nanos = named_spans.first['endTimeUnixNano'].to_i
  start_time_nanos = named_spans.first['startTimeUnixNano'].to_i

  duration_nanos = end_time_nanos - start_time_nanos
  duration = case unit
    when 'ms', 'millis', 'milliseconds' then duration_nanos / 1_000_000
    when 'ns', 'nanos', 'nanoseconds' then duration_nanos
    when 's', 'seconds' then duration_nanos / 1_000_000 / 1000
    else raise Maze::Error::AssertionFailedError.new "Unknown time unit used: #{unit}"
  end

  Maze.check.operator(duration, :>=, min_duration)
end

Then('the {string} span metrics {string} satisfy min <= mean <= max') do |span_name, metric_prefix|
  spans = spans_from_request_list(Maze::Server.list_for('traces'))
  found_spans = spans.find_all { |span| span['name'].eql?(span_name) }

  raise Test::Unit::AssertionFailedError.new "no span named #{span_name} found" if found_spans.empty?

  found_spans.each do |span|
    attributes = span['attributes']

    min_attr = attributes.find { |a| a['key'] == "#{metric_prefix}.min" } || attributes.find { |a| a['key'] == "#{metric_prefix}_min" }
    mean_attr = attributes.find { |a| a['key'] == "#{metric_prefix}.mean" } || attributes.find { |a| a['key'] == "#{metric_prefix}_mean" }
    max_attr = attributes.find { |a| a['key'] == "#{metric_prefix}.max" } || attributes.find { |a| a['key'] == "#{metric_prefix}_max" }

    raise Test::Unit::AssertionFailedError.new "Missing min attribute for #{metric_prefix}" if min_attr.nil?
    raise Test::Unit::AssertionFailedError.new "Missing mean attribute for #{metric_prefix}" if mean_attr.nil?
    raise Test::Unit::AssertionFailedError.new "Missing max attribute for #{metric_prefix}" if max_attr.nil?

    # Handle both doubleValue and intValue
    min = min_attr['value']['doubleValue'] || min_attr['value']['intValue']
    mean = mean_attr['value']['doubleValue'] || mean_attr['value']['intValue']
    max = max_attr['value']['doubleValue'] || max_attr['value']['intValue']

    min = min.to_f
    mean = mean.to_f
    max = max.to_f

    Maze.check.operator(min, :<=, mean, "Min (#{min}) should be <= mean (#{mean})")
    Maze.check.operator(mean, :<=, max, "Mean (#{mean}) should be <= max (#{max})")
  end
end

Then('the {string} span metrics {string} are equal') do |span_name, metric_prefix|
  spans = spans_from_request_list(Maze::Server.list_for('traces'))
  named_spans = spans.select { |s| s['name'].eql?(span_name) }

  raise Test::Unit::AssertionFailedError.new "no span named #{span_name} found" if named_spans.empty?

  named_spans.each do |span|
    attributes = span['attributes']

    min_attr = attributes.find { |a| a['key'] == "#{metric_prefix}.min" }
    mean_attr = attributes.find { |a| a['key'] == "#{metric_prefix}.mean" }
    max_attr = attributes.find { |a| a['key'] == "#{metric_prefix}.max" }

    raise Test::Unit::AssertionFailedError.new "Missing min attribute for #{metric_prefix}" if min_attr.nil?
    raise Test::Unit::AssertionFailedError.new "Missing mean attribute for #{metric_prefix}" if mean_attr.nil?
    raise Test::Unit::AssertionFailedError.new "Missing max attribute for #{metric_prefix}" if max_attr.nil?

    min = min_attr['value']['doubleValue'] || min_attr['value']['intValue']
    mean = mean_attr['value']['doubleValue'] || mean_attr['value']['intValue']
    max = max_attr['value']['doubleValue'] || max_attr['value']['intValue']

    Maze.check.equal(min, mean, "Min (#{min}) should equal mean (#{mean})")
    Maze.check.equal(mean, max, "Mean (#{mean}) should equal max (#{max})")
  end
end

When('I close the app') do
  Maze.driver.terminate_app('com.bugsnag.mazeracer')
end

Then('I should receive no spans') do
  Maze.check.equal(0, Maze::Server.list_for('traces').all.size)
end

Then('I dump all received spans') do
  spans = spans_from_request_list(Maze::Server.list_for('traces'))
  spans.each_with_index do |span, i|
    $logger.info "=== SPAN #{i}: name=#{span['name']} ==="
    span['attributes'].each do |attr|
      $logger.info "  #{attr['key']} => #{attr['value']}"
    end
  end
end
