# frozen_string_literal: true

def execute_command(action, scenario_name = '', scenario_metadata = '')
  command = {
    action: action,
    scenario_name: scenario_name,
    scenario_metadata: scenario_metadata,
    endpoint: 'http://bs-local.com:9339/traces',
  }
  Maze::Server.commands.add command

  # Ensure fixture has read the command
  count = 600
  sleep 0.1 until Maze::Server.commands.remaining.empty? || (count -= 1) < 1
  raise 'Test fixture did not GET /command' unless Maze::Server.commands.remaining.empty?
end

When('I run {string}') do |scenario_name|
  execute_command 'run_scenario', scenario_name
end

When('I run {string} configured as {string}') do |scenario_name, scenario_metadata|
  execute_command 'run_scenario', scenario_name, scenario_metadata
end

When("I relaunch the app after shutdown") do
  max_attempts = 20
  attempts = 0
  state = Maze.driver.app_state('com.bugsnag.mazeracer')
  until (attempts >= max_attempts) || state == :not_running
    attempts += 1
    state = Maze.driver.app_state('com.bugsnag.mazeracer')
    sleep 0.5
  end
  $logger.warn "App state #{state} instead of #{expected_state} after 10s" unless state == :not_running

  Maze.driver.launch_app
end

Then('the {word} payload field {string} attribute {string} equals {string}') do |request_type, field, key, expected|
  assert_attribute request_type, field, key, { 'stringValue' => expected }
end

Then('the {word} payload field {string} attribute {string} is {int}') do |request_type, field, key, expected|
  assert_attribute request_type, field, key, { 'intValue' => expected.to_s }
end

Then('the {word} payload field {string} attribute {string} is true') do |request_type, field, key|
  assert_attribute request_type, field, key, { 'boolValue' => true }
end

Then('the {word} payload field {string} attribute {string} is false') do |request_type, field, key|
  assert_attribute request_type, field, key, { 'boolValue' => false }
end

Then('the {word} payload field {string} attribute {string} matches the regex {string}') do |request_type, field, key, regex_string|
  regex = Regexp.new(regex_string)
  list = Maze::Server.list_for(request_type)
  attributes = Maze::Helper.read_key_path(list.current[:body], "#{field}.attributes")
  attribute = attributes.find { |a| a['key'] == key }
  value = attribute["value"]["intValue"]
  Maze.check.match(regex, value)
end

Then('the {word} payload field {string} attribute {string} is one of:') do |request_type, field, key, possible_values|
  list = Maze::Server.list_for(request_type)
  attributes = Maze::Helper.read_key_path(list.current[:body], "#{field}.attributes")
  attribute = attributes.find { |a| a['key'] == key }

  possible_attributes = possible_values.raw.flatten.map { |v| { 'key' => key, 'value' => { 'stringValue' => v } } }
  Maze.check.not_nil(attribute, "The attribute #{key} is nil")
  Maze.check.include(possible_attributes, attribute)
end

Then('the {word} payload field {string} attribute {string} exists') do |request_type, field, key|
  list = Maze::Server.list_for(request_type)
  attributes = Maze::Helper.read_key_path(list.current[:body], "#{field}.attributes")
  Maze.check.not_nil attributes.find { |a| a['key'] == key }
end

Then('a span {word} equals {string}') do |attribute, expected|
  list = Maze::Server.list_for('traces').all
  spans = list.flat_map { |req| req[:body]['resourceSpans'] }
             .flat_map { |r| r['scopeSpans'] }
             .flat_map { |s| s['spans'] }
             .select { |s| !s.nil? }

  selected_attributes = spans.map { |span| span[attribute] }

  Maze.check.includes selected_attributes, expected
end

def assert_attribute request_type, field, key, expected
  list = Maze::Server.list_for(request_type)
  attributes = Maze::Helper.read_key_path(list.current[:body], "#{field}.attributes")
  Maze.check.equal attributes.find { |a| a['key'] == key }, { 'key' => key, 'value' => expected }
end
