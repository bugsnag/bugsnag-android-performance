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

Then('the {string} span integer attribute {string} is greater than {int}') do |span_name, attribute, expected|
  spans = spans_from_request_list(Maze::Server.list_for('traces'))
  found_spans = spans.find_all { |span| span['name'].eql?(span_name) }
  raise Test::Unit::AssertionFailedError.new "No spans were found with the name #{span_name}" if found_spans.empty?
  raise Test::Unit::AssertionFailedError.new "found #{found_spans.size} spans named #{span_name}, expected exactly one" unless found_spans.size == 1

  attributes = found_spans.first['attributes']
  attribute = attributes.find { |a| a['key'] == attribute }
  value = attribute&.dig 'value', 'intValue'

  Maze.check.operator value.to_i, :>, expected,
                        "The span '#{span_name}' attribute '#{attribute}' (#{value}) is not greater than '#{expected}'"
end

Then('the {string} span has no {string} attribute') do |span_name, attribute|
  spans = spans_from_request_list(Maze::Server.list_for('traces'))
  found_spans = spans.find_all { |span| span['name'].eql?(span_name) }
  raise Test::Unit::AssertionFailedError.new "No spans were found with the name #{span_name}" if found_spans.empty?
  raise Test::Unit::AssertionFailedError.new "found #{found_spans.size} spans named #{span_name}, expected exactly one" unless found_spans.size == 1

  attributes = found_spans.first['attributes']
  attribute = attributes.find { |a| a['key'] == attribute }

  Maze.check.nil(attribute)
end

Then('the {string} span has {word} attribute named {string}') do |span_name, attribute_type, attribute|
  spans = spans_from_request_list(Maze::Server.list_for('traces'))
  found_spans = spans.find_all { |span| span['name'].eql?(span_name) }
  raise Test::Unit::AssertionFailedError.new "No spans were found with the name #{span_name}" if found_spans.empty?
  raise Test::Unit::AssertionFailedError.new "found #{found_spans.size} spans named #{span_name}, expected exactly one" unless found_spans.size == 1

  attributes = found_spans.first['attributes']
  attribute = attributes.find { |a| a['key'] == attribute }

  value = attribute&.dig 'value', "#{attribute_type}Value"

  Maze.check.not_nil value
end

Then('every span string attribute {string} does not exist') do |attribute|
  spans = spans_from_request_list(Maze::Server.list_for('traces'))
  spans.map { |span| Maze.check.nil span['attributes'].find { |a| a['key'] == attribute } }
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
  return list.remaining
             .flat_map { |req| req[:body]['resourceSpans'] }
             .flat_map { |r| r['scopeSpans'] }
             .flat_map { |s| s['spans'] }
             .select { |s| !s.nil? }
end

Then('every span integer attribute {string} matches the regex {string}') do |attribute, pattern|
  regex = Regexp.new(pattern)
  spans = spans_from_request_list(Maze::Server.list_for('traces'))
  spans.map { |span| Maze::check.match(regex, span['attributes'].find { |a| a['key'] == attribute }['value']['intValue']) }
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
