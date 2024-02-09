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

def spans_from_request_list list
  return list.remaining
             .flat_map { |req| req[:body]['resourceSpans'] }
             .flat_map { |r| r['scopeSpans'] }
             .flat_map { |s| s['spans'] }
             .select { |s| !s.nil? }
end
